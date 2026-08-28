package com.sprintstart.sprintstartbackend.connectors.overview.models

import java.util.UUID

/** Adds strict project-scoped batch status updates to connectors that support them. */
interface IProjectScopedSourcePatcher {
    /**
     * Atomically patches existing sources that belong to [projectId].
     *
     * Implementations must reject missing or foreign-project sources before applying any change and
     * return results in the same order as [requestedSources].
     */
    fun patchSources(
        projectId: UUID,
        requestedSources: Map<String, Boolean>,
    ): List<ConnectorSource>
}
