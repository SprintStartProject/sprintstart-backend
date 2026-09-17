package com.sprintstart.sprintstartbackend.connectors.confluence.external

import java.util.UUID

/**
 * Exposes safe Confluence connection metadata needed by other application modules.
 *
 * Implementations must never include persisted credentials in returned source-instance views.
 */
interface ConfluenceConnectionApi {
    /** Lists the Confluence connection IDs owned by a project in stable creation order. */
    fun getConnectionIdsByProject(projectId: UUID): List<UUID>

    /** Lists credential-free Confluence source instances for unified status reporting. */
    fun getSourceInstances(projectId: UUID? = null): List<ConfluenceSourceInstanceDto>
}
