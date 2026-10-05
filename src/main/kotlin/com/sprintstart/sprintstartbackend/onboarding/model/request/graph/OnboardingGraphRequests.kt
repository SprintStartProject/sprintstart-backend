package com.sprintstart.sprintstartbackend.onboarding.model.request.graph

import com.sprintstart.sprintstartbackend.onboarding.model.request.step.CreateOnboardingStepRequest
import java.util.UUID

/** Where one node of an onboarding graph sits on the canvas. */
data class OnboardingGraphNodePosition(
    val id: UUID,
    val graphX: Double,
    val graphY: Double,
)

/**
 * A batch of canvas positions for one graph -- the phases of a path, or the steps and questions of
 * a phase.
 *
 * A batch rather than one node at a time, because the graph view lays out a whole graph at once
 * ("tidy up") and dragging one node of an auto-laid-out graph has to pin all of its siblings too,
 * or they would jump the moment the first one has coordinates and the rest do not.
 */
data class ArrangeOnboardingGraphRequest(
    val nodes: List<OnboardingGraphNodePosition>,
)

/** The complete set of items a node waits on; replaces whatever it waited on before. */
data class ReplaceOnboardingBlockersRequest(
    val blockerIds: Set<UUID>,
)

/**
 * A step a PM adds to somebody's phase *inside* its dependency graph.
 *
 * [waitsOn] are the items it opens after; every item in [unlocks] waits on it from now on. An
 * explicit [graphX]/[graphY] wins over the position worked out from its neighbours -- that is where
 * the PM dropped it.
 */
data class CreateConnectedOnboardingStepRequest(
    val step: CreateOnboardingStepRequest,
    val waitsOn: Set<UUID> = emptySet(),
    val unlocks: Set<UUID> = emptySet(),
    val graphX: Double? = null,
    val graphY: Double? = null,
)
