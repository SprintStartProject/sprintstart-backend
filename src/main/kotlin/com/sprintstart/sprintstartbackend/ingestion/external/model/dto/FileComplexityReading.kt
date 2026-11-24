package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

/**
 * How complicated one file was when it was last read.
 *
 * @property repository The repository key, for example `github:owner/repo`.
 * @property path The path of the file in the repository.
 * @property indentation The sum of the indentation levels of its non-blank lines.
 * @property lines The number of its non-blank lines.
 */
data class FileComplexityReading(
    val repository: String,
    val path: String,
    val indentation: Double,
    val lines: Int,
)
