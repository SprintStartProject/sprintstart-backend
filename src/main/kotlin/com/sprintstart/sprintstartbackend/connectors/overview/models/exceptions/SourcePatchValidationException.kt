package com.sprintstart.sprintstartbackend.connectors.overview.models.exceptions

/** Represents an invalid connector source batch-patch request. */
class SourcePatchValidationException(
    message: String,
) : RuntimeException(message)
