package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.offboarding.model.response.ExportedSnapshotResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskMapResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.StudyExportResponse
import com.sprintstart.sprintstartbackend.offboarding.scoring.Pseudonyms
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Service
import java.time.Clock

/** Exports a project's stored risk maps for study, without naming anybody. */
@Service
class StudyExportService(
    private val snapshots: RiskSnapshotService,
    private val pseudonymFactory: PseudonymFactory,
    private val clock: Clock,
) {
    /** Every stored map of a project, oldest first, with its people replaced by pseudonyms. */
    fun export(projectId: ProjectId): StudyExportResponse {
        val names = pseudonymFactory.forProject(projectId)
        val exported = snapshots
            .list(projectId)
            .sortedBy { it.computedAt }
            .map { summary ->
                ExportedSnapshotResponse(
                    id = summary.id.value,
                    computedAt = summary.computedAt,
                    map = pseudonymised(snapshots.get(projectId, summary.id).map, names),
                )
            }
        return StudyExportResponse(exportedAt = clock.instant(), snapshots = exported)
    }

    private fun pseudonymised(map: RiskMapResponse, names: Pseudonyms): RiskMapResponse =
        map.copy(
            departing = map.departing.map(names::of),
            unknownDeparting = map.unknownDeparting.map(names::of),
            files = map.files.map { file -> file.copy(remainingKnowers = file.remainingKnowers.map(names::of)) },
        )
}
