package com.sprintstart.sprintstartbackend.ingestion.external

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.ArtifactDto
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.AssignedIssue
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.AuthoredArtifact
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.AuthoredPullRequest
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.IngestedIssue
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.RepositoryResponsiveness
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.TaskSourceArtifact
import java.time.Instant
import java.util.UUID

/**
 * Exported ingestion-module API for other backend modules.
 *
 * Exposes read-only ingestion metadata about a component without leaking the ingestion module's
 * internal entities. Other modules should depend on this interface instead of querying the
 * ingestion repositories directly.
 */
@Suppress("TooManyFunctions")
interface ArtifactIngestionApi {
    /**
     * Returns when a component (`owner/repo`) was first ingested, or null when it has no ingested
     * artifacts.
     */
    fun getFirstIngestedAt(component: String): Instant?

    /**
     * Batch variant of [getFirstIngestedAt]. Only components with a known timestamp are present in
     * the returned map.
     */
    fun getFirstIngestedAt(components: Collection<String>): Map<String, Instant>

    /**
     * Returns whether an ingested artifact with [artifactId] exists.
     */
    fun exists(artifactId: UUID): Boolean

    /**
     * Returns the content hash of an ingested artifact, or null if it has none on record.
     *
     * Callers that need to distinguish "no such artifact" from "artifact has no hash" should check
     * [exists] first.
     */
    fun getHash(artifactId: UUID): String?

    /**
     * Returns whether the artifact exists and belongs to the specified project.
     */
    fun existsInProject(projectId: UUID, artifactId: UUID): Boolean

    /**
     * Summarizes what one GitHub account has authored inside a project's ingested artifacts.
     *
     * Reads only the corpus the project already has connected -- no GitHub call. Only issues
     * and pull requests carry an author, so commits and files never contribute (see
     * `Artifact.authorLogin`).
     *
     * @param authorLogin Lower-cased GitHub login to attribute artifacts to.
     * @return One entry per artifact authored by that account; empty when there are none.
     */
    fun getAuthoredWork(projectId: UUID, authorLogin: String): List<AuthoredArtifact>

    /**
     * The pull requests one GitHub account has authored in a project, with the timestamps
     * onboarding measures against.
     *
     * Reads only artifacts already ingested -- no GitHub call.
     */
    fun getAuthoredPullRequests(projectId: UUID, authorLogin: String): List<AuthoredPullRequest>

    /**
     * The tracked issues assigned to one person in a project, with the timestamps onboarding
     * measures against.
     *
     * The non-code counterpart of [getAuthoredPullRequests]. Reads only issues already ingested —
     * no call to the tracker.
     *
     * Attribution is by display name, because that is the only identity the ingested Jira
     * data carries. A blank or unmatched name yields nothing, which callers must read as "no
     * attribution possible" rather than "did no work".
     *
     * @return One entry per issue currently assigned to that name; empty when there are none.
     */
    fun getAssignedIssues(projectId: UUID, assigneeDisplayName: String): List<AssignedIssue>

    /**
     * The ingested artifact one starter-work task was mined from, by its source id.
     *
     * Reads only artifacts already ingested; no GitHub call.
     *
     * @param sourceId The backend's stable identifier, e.g. `github:org/repo:ISSUE:123`.
     * @return The artifact's own text, or null when nothing with that source id is ingested.
     */
    fun getTaskSource(sourceId: String): TaskSourceArtifact?

    /**
     * Every open tracker issue in a project, whoever it belongs to.
     *
     * Assigned issues are returned too, marked, unlike mining's candidate list — filtering
     * here would turn that exclusion into an absence nobody can account for.
     *
     * Reads only artifacts already ingested; no call to GitHub or the tracker.
     *
     * @return One entry per open issue; empty when the project has none ingested.
     */
    fun getOpenIssues(projectId: UUID): List<IngestedIssue>

    /**
     * One ingested issue by its source id, open or not.
     *
     * The single-row counterpart of [getOpenIssues], for acting on an issue somebody browsed.
     * Returns null for a source id that is not ingested or belongs to something that is not an
     * issue — a pull request is not work to hand a newcomer, and silently accepting one would put
     * it in the pool under an issue's name.
     */
    fun getIssue(sourceId: String): IngestedIssue?

    /**
     * How responsive each of a project's repositories is to pull requests.
     *
     * A property of the *repository*, not of any one author: ingestion records when a pull
     * request first got a response but not who responded, so per-person responsiveness cannot
     * be computed.
     *
     * @return One entry per repository that has at least one ingested pull request.
     */
    fun getRepositoryResponsiveness(projectId: UUID): List<RepositoryResponsiveness>

    /** Finds and retrieves an artifact by its unique identifier. */
    fun findArtifactById(artifactId: UUID): ArtifactDto?
}
