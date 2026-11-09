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
import com.sprintstart.sprintstartbackend.blueprint.external.enums.StepType
import com.sprintstart.sprintstartbackend.path.external.HirePathActionsApi
import com.sprintstart.sprintstartbackend.path.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.path.external.enums.StepOrigin
import com.sprintstart.sprintstartbackend.path.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.path.external.model.NewConnectedStep
import com.sprintstart.sprintstartbackend.path.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.path.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.PhaseId
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
 * The buddy's path edit: offering to add a step to the hire's own copy of their path, and adding it
 * once they confirm.
 *
 * Like every path action it writes to the hire's copy only, never to the blueprint it was generated from.
 */
@Component
@Order(HireActionOrder.ADD_PATH_STEP)
class AddPathStepAction(
    private val myPathTool: MyPathTool,
    private val hirePathActionsApi: HirePathActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PERSON
    override val gerund = "add a step to your path"

    override fun isMountedFor(hire: UserId): Boolean = myPathTool.hasPath(hire.value)

    /**
     * Offers to add a step the conversation produced to a phase of the hire's own path.
     *
     * The one action here that writes something the model wrote, so the line is worth restating: it
     * adds to the hire's copy, their PM's blueprint is untouched, and the hire can edit or delete it
     * on their own page like any other step.
     *
     * A description is required rather than optional. A title on its own is a step somebody has to
     * guess at, and the phase it lands in is usually one that came back empty — precisely the case
     * where the hire has nothing else to go on.
     */
    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val user = context.hire
        val phaseId = call.uuidArgument("phase_id")
            ?: return notProposed(
                "No phase_id was provided. Read get_my_onboarding_path and pass the phase_id of the " +
                    "phase the step belongs in.",
            )
        val phase = myPathTool.findPhase(user.value, phaseId)
            ?: return notProposed(
                "No phase of this hire's own path has that id. Read get_my_onboarding_path again.",
            )
        val title = call.textArgument("title")
        val description = call.textArgument("description")
        val placement = PathStepPlacement.inferred(
            phase,
            call.lenientUuidSetArgument("waits_on"),
            call.lenientUuidSetArgument("unlocks"),
        )

        val refusal = when {
            title.isBlank() -> "No title was provided. Say what the step is, in a few words."
            description.isBlank() ->
                "No description was provided. A step with a title and nothing else is one the hire " +
                    "has to guess at — say what doing it involves, then offer it again."
            phase.steps.any { it.title.trim().equals(title, ignoreCase = true) } ->
                "“$title” is already a step of “${phase.title}”, so nothing needs adding. Point them " +
                    "at the one that is there."
            else -> reopensFinishedPhase(user.value, phase) ?: placement.problem()
        }
        if (refusal != null) return notProposed(refusal)

        val where = placement.describe()
        val connection = if (placement.isConnected) {
            " It goes $where in the phase's graph: it opens once what it waits on is done, and what " +
                "it unlocks now waits on it."
        } else {
            " It is not connected to anything, so it is open straight away and is never what comes " +
                "next — if it belongs somewhere in their path, pass waits_on and unlocks."
        }
        val entry = if (placement.entryInferred) {
            " You passed no waits_on, so it opens after what the path says it should (the button " +
                "names it) — if that is not where they meant, offer it again with waits_on."
        } else {
            ""
        }
        val relocks = placement
            .relocksStarted()
            .takeIf { it.isNotEmpty() }
            ?.let {
                " ${it.joinToString(", ")} is already started and will lock again until this step is " +
                    "done — say so before they click."
            }.orEmpty()

        return HireActionOutcome(
            toolResult = "Proposed to the hire: add the step “$title” to the phase " +
                "“${phase.title}” of their own path. They see a confirm button; nothing is added " +
                "unless they click it. This changes their copy only — their PM's blueprint is " +
                "untouched — and they can edit or remove it afterwards. Say what the step is for " +
                "and where it goes before you offer it.$connection$entry$relocks",
            proposal = HireActionProposal(
                action = ADD_PATH_STEP,
                label = if (where.isEmpty()) "Add “$title” to your path" else "Add “$title” $where",
                question = null,
                phaseId = phase.id,
                title = title,
                description = description,
                waitsOnIds = placement.waitsOn.toList(),
                unlocksIds = placement.unlocks.toList(),
            ),
        )
    }

    /**
     * Why adding a step to [phase] would take away something the hire already has, or null.
     *
     * A finished phase is what unlocked every phase that waits on it. A new open step makes it
     * unfinished again, and those phases lock -- a hire asking for one extra step would find the phase
     * they were working in shut. The page's own "add step" has the same effect, which is exactly why
     * the mentor should not reach for it without knowing.
     */
    private fun reopensFinishedPhase(userId: UUID, phase: GetOnboardingPhaseForUserResponse): String? {
        val finished = (phase.steps.isNotEmpty() || phase.questions.isNotEmpty()) &&
            phase.steps.all { it.status == StepStatus.FINISHED || it.status == StepStatus.SKIPPED } &&
            phase.questions.all { it.status == QuestionStatus.PASSED }
        if (!finished) return null
        val waiting = myPathTool.phasesOf(userId).filter { phase.id in it.blockerIds }
        if (waiting.isEmpty()) return null
        return "“${phase.title}” is finished, and ${waiting.joinToString(", ") { "“${it.title}”" }} " +
            "waits on it: a new step there would lock that again until it is done. Put the step in the " +
            "phase they are standing in instead."
    }

    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val user = context.hire
        val phaseId = request.phaseId
        val title = request.title
        if (phaseId == null || title.isNullOrBlank()) {
            return BuddyActionResponse(ok = false, message = "No step was proposed to add.")
        }
        val userId = user.value
        val phase = myPathTool.findPhase(userId, phaseId)
            ?: return BuddyActionResponse(ok = false, message = "That phase isn't on your path.")

        // Checked again: the path can change between the button and the click, and a placement
        // that was sound then can be a loop or point at something finished now.
        val placement = PathStepPlacement(phase, request.waitsOnIds.toSet(), request.unlocksIds.toSet())
        // The finished-phase rule too: a phase the hire finished after the button was shown must not
        // be reopened by clicking it, locking what they have since started in the phases after it.
        if (placement.problem() != null || reopensFinishedPhase(userId, phase) != null) {
            return BuddyActionResponse(
                ok = false,
                message = "Your path changed since this was suggested, so the step wasn't added — ask me again.",
            )
        }

        val created = hirePathActionsApi.placeMyStep(
            authId = authId,
            phaseId = PhaseId(phaseId),
            step = NewConnectedStep(
                step = CreateOnboardingStepRequest(
                    // Next to what it waits on (or in front of what it unlocks), so the list reads in the
                    // order the graph opens it. The end, when it is connected to nothing.
                    position = placement.position(),
                    title = title,
                    description = request.description.orEmpty(),
                    type = StepType.TASK,
                    estimatedMinutes = ADDED_STEP_MINUTES,
                    // Left empty on purpose: an expected outcome is a promise about what doing the step
                    // will leave somebody able to do, and the mentor is not in a position to make one.
                    expectedOutcome = "",
                ),
                // Theirs, but not their idea: the card says it came from the buddy, not from their team.
                origin = StepOrigin.BUDDY,
                waitsOn = placement.waitsOn,
                unlocks = placement.unlocks,
            ),
        )
        return BuddyActionResponse(
            ok = true,
            message = "Added “${created.title}” to “${phase.title}”. It is on your path now, and it " +
                "is yours — edit it or delete it there like any other step.",
        )
    }

    companion object {
        const val ADD_PATH_STEP = "add_path_step"

        /**
         * The estimate a step the buddy added carries.
         *
         * Deliberately a constant the mentor cannot choose. How long a piece of work takes somebody
         * else is not something a model is in a position to say, and a confident "45 min" on a step
         * it invented is worse than a modest default the hire can correct on their own page.
         */
        private const val ADDED_STEP_MINUTES = 15

        val SPEC = BuddyToolSpecDto(
            name = ADD_PATH_STEP,
            description = "Offer to add one step to a phase of the hire's OWN copy of their " +
                "onboarding path. Read get_my_onboarding_path for the phase_id. This does NOT add " +
                "anything by itself; the hire confirms, and afterwards the step is theirs to edit, " +
                "reorder or delete. It never touches their PM's blueprint — the curriculum is the " +
                "PM's, and you are not editing it. Use it for three things and little else: a phase " +
                "that came back empty, where the two of you have worked out something concrete it " +
                "should contain; something real the hire is stuck on that their path does not " +
                "mention, so it stops living in a conversation that is gone tomorrow; and a " +
                "knowledge question they got wrong, once you have gone through the material, when " +
                "what they missed is bigger than one explanation — then one short refresher step in " +
                "that question's phase, saying what to revisit and where, and never the answer. " +
                "Offer it; do not add one after every wrong answer. And whenever the hire ASKS you to " +
                "add a step: it is their copy, so their asking is enough — offer it, do not argue. Give a title " +
                "of a few words and a description saying what doing it involves. Do not offer a " +
                "step for something already on their path, do not add several at once, and do not " +
                "add one just to have added something.\n" +
                "WHERE IT GOES. A phase is a dependency graph, not a list: an item opens once " +
                "everything it waits on is done. A step added with no connections is open at once, " +
                "floats unconnected in their graph view and is never what comes next. So place it: " +
                "waits_on = the ids it should open after, unlocks = the ids that should wait on it. " +
                "To put it in as the NEXT thing, waits_on is the item they are on or just finished, " +
                "and unlocks is what currently waits on that item (the path read lists it under " +
                "\"opens\"). A refresher for a question they missed goes before that question: " +
                "unlocks = [that question_id]. Leave both empty only for a step that genuinely " +
                "depends on nothing and holds nothing up.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("phase_id") {
                        put("type", "string")
                        put("description", "The phase_id from get_my_onboarding_path.")
                    }
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "What the step is, in a few words.")
                    }
                    putJsonObject("description") {
                        put("type", "string")
                        put(
                            "description",
                            "What doing it involves, in one or two sentences the hire can act on.",
                        )
                    }
                    putJsonObject("waits_on") {
                        put("type", "array")
                        putJsonObject("items") { put("type", "string") }
                        put(
                            "description",
                            "step_id/question_id values in the same phase that must be done before " +
                                "this step opens.",
                        )
                    }
                    putJsonObject("unlocks") {
                        put("type", "array")
                        putJsonObject("items") { put("type", "string") }
                        put(
                            "description",
                            "step_id/question_id values in the same phase that should wait on this " +
                                "step. They stop waiting directly on anything in waits_on.",
                        )
                    }
                }
                putJsonArray("required") {
                    add("phase_id")
                    add("title")
                    add("description")
                }
            },
        )
    }
}
