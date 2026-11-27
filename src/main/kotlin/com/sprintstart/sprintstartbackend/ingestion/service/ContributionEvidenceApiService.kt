package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileAuthorshipReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileComplexityReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileKind
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileReview
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FileComplexity
import com.sprintstart.sprintstartbackend.ingestion.repository.FileAuthorshipRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.FileChangeRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.FileComplexityRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.ReviewActivityRepository
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Serves the recorded file changes of a project to other modules. */
@Service
internal class ContributionEvidenceApiService(
    private val fileChangeRepository: FileChangeRepository,
    private val reviewActivityRepository: ReviewActivityRepository,
    private val fileComplexityRepository: FileComplexityRepository,
    private val fileAuthorshipRepository: FileAuthorshipRepository,
) : ContributionEvidenceApi {
    @Transactional(readOnly = true)
    override fun fileContributions(projectId: ProjectId, asOf: Instant): List<FileContribution> =
        fileChangeRepository.findContributions(projectId.value, asOf)

    @Transactional(readOnly = true)
    override fun fileReviews(projectId: ProjectId, asOf: Instant): List<FileReview> =
        reviewActivityRepository.findReviews(projectId.value, asOf)

    @Transactional(readOnly = true)
    override fun fileComplexities(repositories: Collection<String>): List<FileComplexityReading> {
        if (repositories.isEmpty()) return emptyList()
        return fileComplexityRepository.readAll(repositories).mapNotNull(::reading)
    }

    @Transactional(readOnly = true)
    override fun fileAuthorship(repositories: Collection<String>): List<FileAuthorshipReading> =
        if (repositories.isEmpty()) emptyList() else fileAuthorshipRepository.readAll(repositories)

    /**
     * The reading of one measurement.
     *
     * A file measured before kinds were told apart was measured by indentation, which only fits code, so
     * any other file is left out until it is read again.
     */
    private fun reading(row: FileComplexity): FileComplexityReading? {
        val kind = row.kind ?: if (FileKind.of(row.path) == FileKind.CODE) FileKind.CODE else return null
        return FileComplexityReading(row.repository, row.path, kind, row.complexity ?: row.indentation, row.lines)
    }
}
