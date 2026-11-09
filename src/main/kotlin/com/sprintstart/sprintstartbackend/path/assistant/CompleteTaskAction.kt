package com.sprintstart.sprintstartbackend.path.assistant

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
import com.sprintstart.sprintstartbackend.path.external.HirePathActionsApi
import com.sprintstart.sprintstartbackend.path.external.HirePathApi
import com.sprintstart.sprintstartbackend.path.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.path.model.response.task.GetOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.OnboardingTaskId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Offers to tick one line off the checklist of a step of the hire's own path.
 *
 * Separate from [CompleteStepAction] because they are different claims, and the product treats them as
 * such: a step may be finished with lines still open, and unticking a line reopens a finished step. A
 * mentor that could only make the coarse claim would either tick a whole step off for one line of
 * progress or do nothing at all.
 */
@Component
@Order(HireActionOrder.COMPLETE_TASK)
class CompleteTaskAction(
    private val myPathTool: MyPathTool,
    private val hirePathActionsApi: HirePathActionsApi,
    private val hirePathApi: HirePathApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PERSON
    override val gerund = "tick a line off your checklist"

    override fun isMountedFor(hire: UserId): Boolean = myPathTool.hasPath(hire.value)

    /**
     * Offers to tick one line off the checklist of a step.
     *
     * The finer of the two claims, and the reason both exist. A hire who says "I have done the first
     * two" has not finished the step, and a mentor holding only `complete_step` would either overstate
     * that or drop it. The read tool carries the checklist of the step they are on, which is where the
     * task_id comes from.
     */
    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val userId = context.hire.value
        val taskId = call.uuidArgument("task_id")
            ?: return notProposed(
                "No task_id was provided. Read get_my_onboarding_path for the checklist of the step " +
                    "they are on and pass the task_id of the line they mean.",
            )
        val task = findTask(userId, taskId)
            ?: return notProposed(
                "No checklist line of this hire's own path has that id. Read get_my_onboarding_path " +
                    "again — it only carries the checklist of the step they are on, and a line of some " +
                    "other step is not in front of you.",
            )
        if (task.finished) {
            return notProposed(
                "“${task.title}” is already ticked off. Tell them it is already done rather than " +
                    "offering it again.",
            )
        }
        // The task route does not check locks; the page does, by never opening a locked step.
        if (myPathTool.findStep(userId, task.stepId)?.locked == true) {
            return notProposed(
                "The step that “${task.title}” belongs to is locked, so nothing on its checklist can be " +
                    "ticked yet. Say what the step is waiting on instead.",
            )
        }

        return HireActionOutcome(
            toolResult = "Proposed to the hire: tick “${task.title}” off their checklist. They see a " +
                "confirm button and nothing changes unless they click it. Ask whether they have done " +
                "it; ticking a line because the conversation covered it is not the same thing.",
            proposal = HireActionProposal(
                action = COMPLETE_TASK,
                label = "Tick off “${task.title}”",
                question = null,
                onboardingTaskId = task.id,
            ),
        )
    }

    /**
     * One checklist line of the hire's own path, or null.
     *
     * Ownership is the *step's*: a line belongs to this hire exactly when the step it hangs on is on
     * their path, which [MyPathTool.findStep] answers. That is why the unscoped read by id is safe
     * here and would not be on its own.
     */
    private fun findTask(userId: UUID, taskId: UUID): GetOnboardingTaskResponse? {
        val task = runCatching { hirePathApi.task(OnboardingTaskId(taskId)) }.getOrNull()
            ?: return null
        return task.takeIf { myPathTool.findStep(userId, it.stepId) != null }
    }

    /**
     * Ticks the confirmed line off.
     *
     * Read first and written back whole, because the endpoint that owns this takes the task as it
     * should now be rather than a patch. Everything else on it is echoed back unchanged: the mentor is
     * changing one tick box, not editing the hire's checklist.
     */
    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val taskId = request.onboardingTaskId
        if (taskId == null) {
            return BuddyActionResponse(ok = false, message = "No checklist line was proposed to tick off.")
        }
        val task = hirePathApi.myTask(authId, OnboardingTaskId(taskId))
        if (myPathTool.findStep(context.hire.value, task.stepId)?.locked == true) {
            return BuddyActionResponse(
                ok = false,
                message = "That step is locked right now, so its checklist can't be ticked yet.",
            )
        }
        hirePathActionsApi.updateMyTask(
            authId,
            OnboardingTaskId(taskId),
            UpdateOnboardingTaskRequest(
                position = task.position,
                title = task.title,
                description = task.description,
                finished = true,
            ),
        )
        return BuddyActionResponse(
            ok = true,
            message = "Ticked “${task.title}” off. The step itself is still yours to finish when you " +
                "are ready — a checklist does not close it.",
        )
    }

    companion object {
        const val COMPLETE_TASK = "complete_task"

        val SPEC = BuddyToolSpecDto(
            name = COMPLETE_TASK,
            description = "Offer to tick one line off the checklist of the step the hire is on. Read " +
                "get_my_onboarding_path for the checklist and pass that line's task_id. This does " +
                "NOT tick anything by itself; the hire sees a confirm button naming the line. Use it " +
                "when they say they have done part of a step — that is what this is for, and it is " +
                "why it is separate from complete_step: a step can be finished with lines still open, " +
                "and finishing a step is a bigger claim than ticking a line. Ask; do not tick a line " +
                "off because the conversation covered it.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("task_id") {
                        put("type", "string")
                        put("description", "The task_id from the checklist in get_my_onboarding_path.")
                    }
                }
                putJsonArray("required") { add("task_id") }
            },
        )
    }
}
