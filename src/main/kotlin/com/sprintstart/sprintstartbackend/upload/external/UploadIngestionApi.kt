package com.sprintstart.sprintstartbackend.upload.external

/**
 * Upload module API for ingesting repository-backed text content.
 *
 * The GitHub module uses this interface instead of publishing large payloads through persistent
 * Modulith events. That keeps the module boundary explicit while avoiding storage limits in the
 * event publication table.
 */
interface UploadIngestionApi {
    suspend fun ingestGithubFile(path: String, content: String, sourceUrl: String)
}
