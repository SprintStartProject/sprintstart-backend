package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.ingestion.repository.FileChangeRepository
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Serves the recorded file changes of a project to other modules. */
@Service
internal class ContributionEvidenceApiService(
    private val fileChangeRepository: FileChangeRepository,
) : ContributionEvidenceApi {
    @Transactional(readOnly = true)
    override fun fileContributions(projectId: ProjectId, asOf: Instant): List<FileContribution> =
        fileChangeRepository.findContributions(projectId.value, asOf)
}
