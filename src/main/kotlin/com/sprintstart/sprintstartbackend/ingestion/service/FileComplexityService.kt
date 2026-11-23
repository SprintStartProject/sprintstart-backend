package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.entity.FileComplexity
import com.sprintstart.sprintstartbackend.ingestion.repository.FileComplexityRepository
import com.sprintstart.sprintstartbackend.ingestion.util.IndentationComplexity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** Keeps the measured complexity of every file ingestion has read. */
@Service
internal class FileComplexityService(
    private val repository: FileComplexityRepository,
    private val clock: Clock,
) {
    /**
     * Measures a file that was read and stores the result in place of an earlier measurement.
     *
     * @param repositoryKey The repository the file lives in, for example `github:owner/repo`.
     * @param path The path of the file in the repository.
     * @param text The text of the file.
     */
    @Transactional
    fun measure(repositoryKey: String, path: String, text: String) {
        val measure = IndentationComplexity.of(text)
        val existing = repository.findByRepositoryAndPath(repositoryKey, path)
        if (existing == null) {
            repository.save(
                FileComplexity(
                    repository = repositoryKey,
                    path = path,
                    indentation = measure.indentation,
                    lines = measure.lines,
                    measuredAt = clock.instant(),
                ),
            )
        } else {
            existing.indentation = measure.indentation
            existing.lines = measure.lines
            existing.measuredAt = clock.instant()
        }
    }

    /** Forgets the measurement of a file that was removed from the repository. */
    @Transactional
    fun forget(repositoryKey: String, path: String) {
        repository.deleteByFile(repositoryKey, path)
    }
}
