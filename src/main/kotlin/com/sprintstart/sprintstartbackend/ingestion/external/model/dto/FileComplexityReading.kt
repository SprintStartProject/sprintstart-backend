package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

/**
 * How complicated one file was when it was last read.
 *
 * @property repository The repository key, for example `github:owner/repo`.
 * @property path The path of the file in the repository.
 * @property kind What the file holds, which gives [complexity] its unit.
 * @property complexity How much the file holds, in the unit of its [kind].
 * @property lines The number of its non-blank lines.
 */
data class FileComplexityReading(
    val repository: String,
    val path: String,
    val kind: FileKind,
    val complexity: Double,
    val lines: Int,
)
