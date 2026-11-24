package com.sprintstart.sprintstartbackend.offboarding.model.response

import java.time.Instant
import java.util.UUID

/**
 * The risk maps a project stored, with every person replaced by a pseudonym, for study use.
 *
 * Nobody is named: the leaving people, the people who stay on a file and the people no record belongs to
 * all appear as pseudonyms, the same one for the same person within the project. Labels are left out because
 * they are free text. File paths and repository names are kept, since they are what the maps are about.
 *
 * @property exportedAt When the export was made.
 * @property snapshots Every stored map, oldest first.
 */
data class StudyExportResponse(
    val exportedAt: Instant,
    val snapshots: List<ExportedSnapshotResponse>,
)

/**
 * One stored risk map in an export.
 *
 * @property id The stored map's identity, to refer to it.
 * @property computedAt When it was computed.
 * @property map The ranking with its settings and its people pseudonymised.
 */
data class ExportedSnapshotResponse(
    val id: UUID,
    val computedAt: Instant,
    val map: RiskMapResponse,
)
