package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileKind
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FileComplexity
import com.sprintstart.sprintstartbackend.ingestion.repository.FileComplexityRepository
import com.sprintstart.sprintstartbackend.ingestion.util.FileMeasure
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
     * @param kind What the file holds; told by its name unless given, as for a documentation page.
     */
    @Transactional
    fun measure(repositoryKey: String, path: String, text: String, kind: FileKind = FileKind.of(path)) {
        val measure = FileMeasure.of(path, text, kind)
        val existing = repository.findByRepositoryAndPath(repositoryKey, path)
        if (existing == null) {
            repository.save(
                FileComplexity(
                    repository = repositoryKey,
                    path = path,
                    indentation = measure.indentation,
                    lines = measure.lines,
                    measuredAt = clock.instant(),
                    kind = measure.kind,
                    complexity = measure.complexity,
                ),
            )
        } else {
            existing.indentation = measure.indentation
            existing.lines = measure.lines
            existing.measuredAt = clock.instant()
            existing.kind = measure.kind
            existing.complexity = measure.complexity
        }
    }

    /** Forgets the measurement of a file that was removed from the repository. */
    @Transactional
    fun forget(repositoryKey: String, path: String) {
        repository.deleteByFile(repositoryKey, path)
    }
}
