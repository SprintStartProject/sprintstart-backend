package com.sprintstart.sprintstartbackend.connectors.github.models.exceptions

data class SourceNotFoundException(
    val sourceId: String,
) : RuntimeException("Source with id $sourceId not found.")
