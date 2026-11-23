package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import org.springframework.stereotype.Component

/** Reads how complicated the files of a project were when they were last read. */
@Component
class FileComplexities(
    private val api: ContributionEvidenceApi,
) {
    /** The indentation complexity of every measured file in [repositories]. */
    fun of(repositories: Collection<String>): Map<FileKey, Double> =
        api.fileComplexities(repositories).associate { FileKey(it.repository, it.path) to it.indentation }
}
