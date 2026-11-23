package com.sprintstart.sprintstartbackend.path.external.model

import com.sprintstart.sprintstartbackend.path.external.enums.StepOrigin
import com.sprintstart.sprintstartbackend.path.model.request.step.CreateOnboardingStepRequest
import java.util.UUID

/**
 * A step a hire adds to their own path, and where it goes in the phase's graph.
 *
 * @property waitsOn The items of the same phase the new step waits on.
 * @property unlocks The items of the same phase that wait on the new step.
 */
data class NewConnectedStep(
    val step: CreateOnboardingStepRequest,
    val origin: StepOrigin,
    val waitsOn: Set<UUID>,
    val unlocks: Set<UUID>,
)
