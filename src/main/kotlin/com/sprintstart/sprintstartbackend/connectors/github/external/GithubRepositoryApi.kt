package com.sprintstart.sprintstartbackend.connectors.github.external

import java.util.UUID

/**
 * Module-facing API for reading GitHub repository connection metadata needed outside the
 * GitHub module.
 */
interface GithubRepositoryApi {
    /**
     * Returns the project ids linked to one stored GitHub repository connection.
     *
     * @param id The internal repository connection identifier.
     * @return All SprintStart project ids currently linked to the repository connection.
     */
    fun getRepositoryProjectIdsById(id: UUID): Set<UUID>
}
