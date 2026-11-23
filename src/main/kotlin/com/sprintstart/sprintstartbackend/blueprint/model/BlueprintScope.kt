package com.sprintstart.sprintstartbackend.blueprint.model

import java.util.UUID

sealed interface BlueprintScope {
    data object Global : BlueprintScope

    data class Project(
        val projectId: UUID,
    ) : BlueprintScope
}
