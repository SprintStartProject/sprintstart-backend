package com.sprintstart.sprintstartbackend.shared.git

import org.springframework.stereotype.Service
import java.nio.file.Path

/**
 * Runs Git CLI commands against a local repository.
 *
 * Exists as an interface so tests can record and stub the Git commands a caller issues without
 * touching the filesystem or the network.
 */
interface GitOperationRunner {
    /**
     * Runs [op] inside [path] and returns its combined output.
     *
     * @throws RuntimeException if the command exits with a non-zero exit code.
     */
    fun exec(path: Path, op: ProcessBuilder): String
}

/**
 * Default [GitOperationRunner] that runs the command through [OnDiskOperations].
 */
@Service
class DefaultGitOperationRunner : GitOperationRunner {
    override fun exec(path: Path, op: ProcessBuilder): String =
        OnDiskOperations.exec(path, op)
}
