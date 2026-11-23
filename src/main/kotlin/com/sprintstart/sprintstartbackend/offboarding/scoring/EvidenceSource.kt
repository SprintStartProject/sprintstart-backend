package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import java.time.Instant

/**
 * One kind of evidence about who knows a file.
 *
 * A new kind of evidence is a new implementation: the scorer merges the histories of every source
 * and weighs them by the [WeightProfile], so nothing else changes.
 */
interface EvidenceSource {
    /** The name under which the profile weighs this source and the risk map reports it. */
    val id: SourceId

    /**
     * The files this source has evidence for, up to a moment.
     *
     * Every touch carries [id] as its source. A file is identified by repository and latest path, so
     * histories of different sources for the same file are merged.
     *
     * @param projectId The project whose evidence is read.
     * @param asOf Evidence from after this moment is left out, so a past state can be replayed.
     */
    fun histories(projectId: ProjectId, asOf: Instant): List<FileHistory>
}
