package com.sprintstart.sprintstartbackend.github.models.client.dto

/**
 * Some file that changed in some way in the next update.
 */
sealed class ChangedFile(
    open val relativePath: String,
)

/**
 * Represents a file that was found to be changed in an update.
 */
data class ModifiedFile(
    override val relativePath: String,
    val content: String,
) : ChangedFile(relativePath)

/**
 * Represents a file that was deleted, and will be un-ingested in the next update.
 */
data class DeletedFile(
    override val relativePath: String,
) : ChangedFile(relativePath)
