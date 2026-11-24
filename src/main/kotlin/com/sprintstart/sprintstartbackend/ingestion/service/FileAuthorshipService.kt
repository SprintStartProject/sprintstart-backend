package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.entity.FileAuthorship
import com.sprintstart.sprintstartbackend.ingestion.repository.FileAuthorshipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Keeps who wrote the lines of every file ingestion has read. */
@Service
internal class FileAuthorshipService(
    private val repository: FileAuthorshipRepository,
) {
    /**
     * Stores who wrote the lines of a file that was read, in place of what was stored before.
     *
     * Nothing is changed when [authorLines] is empty, because Git could not say who wrote the lines
     * and an earlier answer is better than none.
     *
     * @param repositoryKey The repository the file lives in, for example `github:owner/repo`.
     * @param path The path of the file in the repository.
     * @param authorLines How many lines each author email wrote.
     */
    @Transactional
    fun record(repositoryKey: String, path: String, authorLines: Map<String, Int>) {
        if (authorLines.isEmpty()) return
        repository.deleteByFile(repositoryKey, path)
        repository.saveAll(
            authorLines.map { (email, lines) -> FileAuthorship(null, repositoryKey, path, email, lines) },
        )
    }

    /** Forgets the authorship of a file that was removed from the repository. */
    @Transactional
    fun forget(repositoryKey: String, path: String) {
        repository.deleteByFile(repositoryKey, path)
    }
}
