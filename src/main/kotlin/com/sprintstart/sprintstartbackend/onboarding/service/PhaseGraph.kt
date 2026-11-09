package com.sprintstart.sprintstartbackend.onboarding.service

import java.util.UUID

/** What every line of the current phase needs to name its neighbours. */
internal data class PhaseGraph(
    val numbers: Map<UUID, Int>,
    val titles: Map<UUID, String>,
    val opens: Map<UUID, List<UUID>>,
)
