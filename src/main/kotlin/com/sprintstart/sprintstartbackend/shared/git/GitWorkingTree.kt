package com.sprintstart.sprintstartbackend.shared.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * The result of reading one tracked file from a clone.
 *
 * The three failures are kept apart because callers react differently to each: [Binary] is expected
 * and skipped silently, while [TooLarge] and [Unreadable] are reported so that a file which cannot
 * be ingested is visible instead of quietly missing from the index.
 */
sealed interface GitFileRead {
    /** Text content together with the hash computed from the same bytes. */
    data class Text(
        val text: String,
        val sha256: String,
    ) : GitFileRead

    /** A file whose type is never ingested, such as an image or an archive. */
    data object Binary : GitFileRead

    /** A file above the configured ingest size limit, which was not read. */
    data class TooLarge(
        val sizeBytes: Long,
    ) : GitFileRead

    /** A file that exists but could not be read as text. */
    data class Unreadable(
        val reason: String,
    ) : GitFileRead
}

/**
 * Reads the tracked files of a local clone.
 *
 * Files are enumerated with `git ls-files`, not by walking the directory tree. That is both faster
 * and more correct: it reads the index instead of the filesystem, so it never descends into `.git`
 * (which holds a full copy of the object store), never picks up build output or other untracked
 * files, and returns only paths that are part of the revision.
 *
 * Each file is read once. The hash is computed from the same byte array that is decoded into text,
 * so a file is not read, and not hashed, twice.
 *
 * Text is decoded strictly. A lenient decode would replace invalid bytes with U+FFFD and hand the
 * caller mangled content that looks like a successful read, so a file that is not valid UTF-8 is
 * reported as [GitFileRead.Unreadable] instead. Decoded text containing NUL takes the same path:
 * NUL is valid UTF-8, so the decode accepts it, but PostgreSQL text columns reject it — and the
 * rejection would surface as a database error in the synchronous file listener, failing the whole
 * files phase instead of just skipping the file.
 *
 * @property onDiskOperations The factory supplying the Git commands themselves.
 * @property gitRunner The runner used to execute them.
 * @property config The Git configuration, which bounds file size and batch behaviour.
 */
@Service
class GitWorkingTree(
    private val onDiskOperations: OnDiskOperations,
    private val gitRunner: GitOperationRunner,
    private val config: GitConfig,
) {
    /**
     * Lists the repository-relative paths of every tracked file in a clone.
     *
     * @param repositoryPath The local clone to enumerate.
     * @return Relative paths of all tracked files, in Git's order.
     * @throws RuntimeException if the path is not a usable Git repository.
     */
    suspend fun trackedFiles(repositoryPath: Path): List<String> =
        withContext(Dispatchers.IO) {
            gitRunner
                .exec(repositoryPath, onDiskOperations.gitListFiles())
                .split(NUL)
                .filter(String::isNotEmpty)
        }

    /**
     * Reads one tracked file, hashing it in the same pass.
     *
     * @param repositoryPath The local clone the path is relative to.
     * @param relativePath The repository-relative path to read.
     * @return The file's text and hash, or the reason it was not ingested.
     */
    suspend fun readFile(repositoryPath: Path, relativePath: String): GitFileRead {
        if (isBinary(relativePath)) return GitFileRead.Binary

        val absolutePath = repositoryPath.resolve(relativePath)
        return withContext(Dispatchers.IO) {
            runCatching {
                val sizeBytes = Files.size(absolutePath)
                if (sizeBytes > config.ingest.maxFileSizeBytes) {
                    return@runCatching GitFileRead.TooLarge(sizeBytes)
                }

                val bytes = Files.readAllBytes(absolutePath)
                val text = decodeStrictly(bytes)
                    ?: return@runCatching GitFileRead.Unreadable("not valid UTF-8")
                if (NUL in text) {
                    return@runCatching GitFileRead.Unreadable("contains NUL bytes, which text columns cannot store")
                }

                GitFileRead.Text(text = text, sha256 = sha256(bytes))
            }.getOrElse { e -> GitFileRead.Unreadable(e.message ?: e::class.simpleName ?: "unreadable") }
        }
    }

    /**
     * Reports whether a path is a file type that is never ingested.
     *
     * Classification is by extension only, which is cheap and needs no read. The size limit in
     * [readFile] is what catches binary files that do not advertise themselves by extension, such as
     * a generated single-line script or a large data file.
     */
    fun isBinary(relativePath: String): Boolean {
        val fileName = relativePath.substringAfterLast('/')
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in BINARY_EXTENSIONS
    }

    /** Decodes UTF-8 strictly, returning `null` when the bytes are not valid UTF-8. */
    private fun decodeStrictly(bytes: ByteArray): String? =
        runCatching {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrElse { e -> if (e is CharacterCodingException) null else throw e }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        /** Terminator used by `git ls-files -z`, which makes any file name representable. */
        const val NUL = '\u0000'

        /** File types that are skipped without being read. */
        val BINARY_EXTENSIONS = setOf(
            // images
            "png",
            "jpg",
            "jpeg",
            "gif",
            "bmp",
            "ico",
            "svg",
            "webp",
            // compiled
            "class",
            "jar",
            "war",
            "ear",
            // archives
            "zip",
            "tar",
            "gz",
            "rar",
            // binaries
            "exe",
            "dll",
            "so",
            "dylib",
            // media
            "mp3",
            "mp4",
            "wav",
            "avi",
            // documents
            "pdf",
            "doc",
            "docx",
            "xls",
            "xlsx",
        )
    }
}
