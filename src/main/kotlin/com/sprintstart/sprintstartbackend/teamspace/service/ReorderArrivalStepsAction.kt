package com.sprintstart.sprintstartbackend.teamspace.service

import com.sprintstart.sprintstartbackend.arrival.external.ArrivalAuthoringApi
import com.sprintstart.sprintstartbackend.assistant.external.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.textArray
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.stereotype.Component

/** The complete order, by key, that a confirmed reorder applies. */
@Serializable
data class ReorderArrivalStepsParams(
    @SerialName("ordered_keys") val orderedKeys: List<String>,
)

/**
 * Offers a whole new order for the project's arrival list.
 *
 * Always the complete list, never a move, so concurrent reorders cannot interleave into an order nobody chose.
 */
@Component
class ReorderArrivalStepsAction(
    private val arrivalSteps: ArrivalAuthoringApi,
) : TypedTeamAction<ReorderArrivalStepsParams>(ReorderArrivalStepsParams.serializer()) {
    override val area = TeamArea.ARRIVAL
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "reorder_arrival_steps",
        description = "Offer a new order for this project's arrival list. Pass every key the project has, " +
            "once each, in the order they should appear — call list_arrival_steps first and reorder that " +
            "list. A partial list is refused. This does NOT reorder anything by itself.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("ordered_keys") {
                    put("type", "array")
                    put("description", "Every arrival step key on this project, once each, in the new order.")
                    putJsonObject("items") { put("type", "string") }
                }
            }
            putJsonArray("required") { add("ordered_keys") }
        },
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val steps = arrivalSteps.authoredOn(ProjectId(context.projectId))
        if (steps.isEmpty()) {
            return TeamActionDraft.Refused("This project has no arrival steps to reorder.")
        }
        val ordered = call.arguments.textArray("ordered_keys").map { normalizeKey(it) }
        val refusal = disagreement(ordered, steps.map { it.key })
        if (refusal != null) {
            return TeamActionDraft.Refused(refusal)
        }
        if (ordered == steps.map { it.key }) {
            return TeamActionDraft.Refused("That is the order the list is already in.")
        }

        val titles = steps.associate { it.key to it.title }
        return proposed(
            params = ReorderArrivalStepsParams(ordered),
            label = "Reorder ${ordered.size} arrival steps",
            preview = buildString {
                appendLine("Put this project's arrival list in this order:")
                appendLine()
                ordered.forEachIndexed { index, key -> appendLine("${index + 1}. ${titles[key]} [key: $key]") }
                appendLine()
                append("Only the order changes. No step is added, removed or reworded.")
            }.trim(),
        )
    }

    /** Why [ordered] is not a rearrangement of [present], or null when it is one. */
    private fun disagreement(ordered: List<String>, present: List<String>): String? {
        val duplicate = ordered
            .groupingBy { it }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
        if (duplicate != null) {
            return "'${duplicate.key}' appears ${duplicate.value} times. Each key goes in the list once."
        }
        val unknown = ordered.firstOrNull { it !in present }
        if (unknown != null) {
            return "This project has no arrival step '$unknown'. Call list_arrival_steps for the keys it has."
        }
        val missing = present.filter { it !in ordered }
        if (missing.isNotEmpty()) {
            return "The order left out ${missing.joinToString(", ") { "'$it'" }}. Pass every key, once each, " +
                "so the whole list is accounted for."
        }
        return null
    }

    override fun recheckParams(params: ReorderArrivalStepsParams, context: TeamToolContext): String? {
        val present = arrivalSteps.authoredOn(ProjectId(context.projectId)).map { it.key }
        return "The arrival list changed since, so the order was not applied."
            .takeIf { disagreement(params.orderedKeys, present) != null }
    }

    override suspend fun performParams(params: ReorderArrivalStepsParams, context: TeamToolContext): String {
        arrivalSteps.reorder(ProjectId(context.projectId), params.orderedKeys)
        return "Reordered. This project's arrival list now runs in the order you confirmed."
    }
}
