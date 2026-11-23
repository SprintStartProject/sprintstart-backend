package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.offboarding.model.RiskSnapshotId
import com.sprintstart.sprintstartbackend.offboarding.model.SnapshotRow
import com.sprintstart.sprintstartbackend.offboarding.model.SnapshotSummary
import com.sprintstart.sprintstartbackend.offboarding.model.StoredComparison
import com.sprintstart.sprintstartbackend.offboarding.model.StoredRiskMap
import com.sprintstart.sprintstartbackend.offboarding.model.entity.RiskSnapshot
import com.sprintstart.sprintstartbackend.offboarding.model.entity.SnapshotCounts
import com.sprintstart.sprintstartbackend.offboarding.model.entity.SnapshotTimes
import com.sprintstart.sprintstartbackend.offboarding.model.exception.RiskSnapshotNotFoundException
import com.sprintstart.sprintstartbackend.offboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.offboarding.model.request.RiskRequest
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskMapResponse
import com.sprintstart.sprintstartbackend.offboarding.repository.RiskSnapshotRepository
import com.sprintstart.sprintstartbackend.offboarding.scoring.MapComparisons
import com.sprintstart.sprintstartbackend.offboarding.scoring.RankedFile
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Clock

/**
 * Keeps the risk maps a project's managers computed, so a departure can be looked at again and
 * ranked under different settings side by side.
 */
@Service
class RiskSnapshotService(
    private val snapshots: RiskSnapshotRepository,
    private val risks: OffboardingRiskService,
    private val json: ObjectMapper,
    private val clock: Clock,
) {
    /**
     * Computes a departure's risk map and stores it with its settings, date and moment of computation.
     *
     * @param projectId The project whose history is read.
     * @param request Who leaves and how to weigh the evidence; the date defaults to now.
     * @param label A name for the map, such as the settings under test; blank counts as none.
     */
    @Transactional
    fun create(projectId: ProjectId, request: RiskRequest, label: String?): StoredRiskMap {
        val map = risks.riskMap(projectId, request).toResponse(Int.MAX_VALUE)
        val saved = snapshots.save(
            RiskSnapshot(
                projectId = projectId.value,
                label = label?.trim()?.takeIf { it.isNotEmpty() },
                times = SnapshotTimes(computedAt = clock.instant(), asOf = map.asOf),
                departing = map.departing.joinToString("\n"),
                profileName = map.settings.name,
                counts = SnapshotCounts(map.summary.files, map.summary.orphaned, map.summary.hoarded),
                payload = json.writeValueAsString(map),
            ),
        )
        return StoredRiskMap(summaryOf(saved), map)
    }

    /** A project's stored risk maps, newest first. */
    @Transactional(readOnly = true)
    fun list(projectId: ProjectId): List<SnapshotSummary> = snapshots
        .findSummaries(
            projectId.value,
        ).map(SnapshotRow::toSummary)

    /**
     * One stored risk map with its ranking.
     *
     * @throws RiskSnapshotNotFoundException when the project has no such map.
     */
    @Transactional(readOnly = true)
    fun get(projectId: ProjectId, snapshot: RiskSnapshotId): StoredRiskMap {
        val stored = find(projectId, snapshot)
        return StoredRiskMap(summaryOf(stored), json.readValue(stored.payload, RiskMapResponse::class.java))
    }

    /**
     * Throws a stored risk map away.
     *
     * @throws RiskSnapshotNotFoundException when the project has no such map.
     */
    @Transactional
    fun delete(projectId: ProjectId, snapshot: RiskSnapshotId) {
        val removed = snapshots.deleteInProject(projectId.value, snapshot.value)
        if (removed == 0) throw RiskSnapshotNotFoundException(snapshot.value.toString())
    }

    /**
     * Compares the rankings of two stored risk maps of one project.
     *
     * @throws RiskSnapshotNotFoundException when the project has no such map.
     */
    @Transactional(readOnly = true)
    fun compare(projectId: ProjectId, left: RiskSnapshotId, right: RiskSnapshotId): StoredComparison {
        val first = get(projectId, left)
        val second = get(projectId, right)
        return StoredComparison(
            left = first.summary,
            right = second.summary,
            comparison = MapComparisons.compare(rankedFilesOf(first), rankedFilesOf(second)),
        )
    }

    private fun find(projectId: ProjectId, snapshot: RiskSnapshotId): RiskSnapshot =
        snapshots.findInProject(projectId.value, snapshot.value)
            ?: throw RiskSnapshotNotFoundException(snapshot.value.toString())

    private fun rankedFilesOf(stored: StoredRiskMap): List<RankedFile> =
        stored.map.files.map { RankedFile(it.repository, it.path, it.level, it.score) }

    private fun summaryOf(snapshot: RiskSnapshot): SnapshotSummary =
        SnapshotRow(
            id = requireNotNull(snapshot.id) { "A stored risk map has an identity" },
            label = snapshot.label,
            computedAt = snapshot.times.computedAt,
            asOf = snapshot.times.asOf,
            departing = snapshot.departing,
            profileName = snapshot.profileName,
            files = snapshot.counts.files,
            orphaned = snapshot.counts.orphaned,
            hoarded = snapshot.counts.hoarded,
        ).toSummary()
}
