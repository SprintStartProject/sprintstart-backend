package com.sprintstart.sprintstartbackend.connectors.notion.external

import java.util.UUID

/** Exposes credential-free Notion connection data needed by other application modules. */
interface NotionConnectionApi {
    /** Lists the Notion connection IDs owned by one project in stable creation order. */
    fun getConnectionIdsByProject(projectId: UUID): List<UUID>

    /** Lists safe Notion source instances for unified ingestion status reporting. */
    fun getSourceInstances(projectId: UUID? = null): List<NotionSourceInstanceDto>
}
