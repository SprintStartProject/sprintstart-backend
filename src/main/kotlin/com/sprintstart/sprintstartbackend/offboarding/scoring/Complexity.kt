package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileKind

/**
 * How much one file holds, in the unit of its kind.
 *
 * Values of different kinds are not comparable: words of a page say nothing against the indentation
 * of code, so a file is only ranked against files of its own [kind].
 */
data class Complexity(
    val kind: FileKind,
    val value: Double,
)
