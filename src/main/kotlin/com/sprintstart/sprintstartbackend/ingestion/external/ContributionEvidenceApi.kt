package com.sprintstart.sprintstartbackend.ingestion.external

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileAuthorshipReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileComplexityReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileReview
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

    /**
     * Every file of a pull request that a reviewer submitted a review of, up to a moment, oldest first.
     *
     * A reviewer who reviewed one pull request more than once is listed with their latest review, and a
     * pull request's own author is never listed as its reviewer. A pull request that changed too many
     * files to list contributes nothing.
     *
     * @param projectId The project whose repositories are read.
     * @param asOf Reviews submitted after this moment are left out, so a past state can be replayed.
     * @return One entry per reviewer and file; empty when the project has no recorded reviews.
     */
    fun fileReviews(projectId: ProjectId, asOf: Instant): List<FileReview>

    /**
     * How complicated each file of the given repositories was when it was last read.
     *
     * Files that were never read as text, such as binaries, have no entry.
     *
     * @param repositories The repository keys, for example `github:owner/repo`.
     * @return One entry per measured file; empty when none was measured.
     */
    fun fileComplexities(repositories: Collection<String>): List<FileComplexityReading>

    /**
     * How many of the lines each file of the given repositories has now each author wrote, by `git blame`.
     *
     * Files for which Git could not say have no entry.
     *
     * @param repositories The repository keys, for example `github:owner/repo`.
     * @return One entry per file and author; empty when nothing was read.
     */
    fun fileAuthorship(repositories: Collection<String>): List<FileAuthorshipReading>
}
