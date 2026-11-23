package com.sprintstart.sprintstartbackend.starterwork.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyActionResponse
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.HireAction
import com.sprintstart.sprintstartbackend.assistant.external.HireActionContext
import com.sprintstart.sprintstartbackend.assistant.external.HireActionOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireActionOutcome
import com.sprintstart.sprintstartbackend.assistant.external.HireActionProposal
import com.sprintstart.sprintstartbackend.assistant.external.HireActionRequest
import com.sprintstart.sprintstartbackend.assistant.external.HireActionScope
import com.sprintstart.sprintstartbackend.assistant.external.offeredToHire
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.StarterWorkTaskId
import com.sprintstart.sprintstartbackend.starterwork.external.HireStarterWorkActionsApi
import com.sprintstart.sprintstartbackend.starterwork.external.enums.GoalClaimant
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Offers to make a suggested starter-work task the hire's goal on their project. */
@Component
@Order(HireActionOrder.CLAIM_GOAL)
class ClaimGoalAction(
    private val hireStarterWorkActions: HireStarterWorkActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PROJECT
    override val gerund = "claim a goal"

    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val taskId = call.uuidArgument("task_id")
            ?: return HireActionOutcome(
                "No task_id was provided. Read the suggested tasks and offer to claim one by its task_id.",
                null,
            )
        return HireActionOutcome(
            toolResult = offeredToHire(context.requireProject.name, LABEL),
            proposal = HireActionProposal(action = CLAIM_GOAL, label = LABEL, question = null, taskId = taskId),
        )
    }

    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val taskId = request.taskId
            ?: return BuddyActionResponse(ok = false, message = "No task was proposed to claim.")
        // Claiming also pins the current-task card (see `HireStarterWorkActionsApi.claimGoal`), so the
        // message below can promise it is on the board.
        val goal = hireStarterWorkActions.claimGoal(
            authId,
            context.requireProject.id,
            StarterWorkTaskId(taskId),
            GoalClaimant.BUDDY,
        )
        return BuddyActionResponse(
            ok = true,
            message = "You're now working toward “${goal.title}” — I'll shape your next steps around it. " +
                "It's on your board too, so you won't have to go looking for it.",
        )
    }

    companion object {
        const val CLAIM_GOAL = "claim_goal"
        const val LABEL = "Work toward this task"

        val SPEC = BuddyToolSpecDto(
            name = CLAIM_GOAL,
            description = "Offer to claim a suggested starter-work task as the hire's goal — the " +
                "contribution their learning plan then aims at. This does NOT claim anything by " +
                "itself; it shows the hire a confirm button and runs only if they click. Use when " +
                "the hire picks one of the tasks get_suggested_tasks returned, passing that " +
                "task's task_id.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("task_id") {
                        put("type", "string")
                        put("description", "The task_id of the suggested task the hire picked.")
                    }
                }
                putJsonArray("required") { add("task_id") }
            },
        )
    }
}
