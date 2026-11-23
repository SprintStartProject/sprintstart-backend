package com.sprintstart.sprintstartbackend.ingestion.external

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import java.time.Instant

/**
 * Read access to who changed which file, for modules that reason about who knows what.
 *
 * Reads only what ingestion already stored; it calls no provider.
 */
interface ContributionEvidenceApi {
    /**
     * Every file change recorded for a project's repositories up to a moment, oldest first.
     *
     * A commit that changed too many files to list contributes nothing, and a merge commit lists no
     * files, so the result is a lower bound of what happened.
     *
     * @param projectId The project whose repositories are read.
     * @param asOf Changes made after this moment are left out, so a past state can be replayed.
     * @return One entry per commit and file; empty when the project has no recorded history.
     */
    fun fileContributions(projectId: ProjectId, asOf: Instant): List<FileContribution>
}
