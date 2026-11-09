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
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.starterwork.external.HireStarterWorkActionsApi
import com.sprintstart.sprintstartbackend.starterwork.external.HireStarterWorkApi
import com.sprintstart.sprintstartbackend.starterwork.external.enums.ProficiencyLevel
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Offers to record where a short chat placed the hire on one competency.
 *
 * Not project-scoped: the competency ledger is global, because a skill somebody has does not stop being true
 * on their second project.
 */
@Component
@Order(HireActionOrder.RECORD_ASSESSMENT)
class RecordAssessmentAction(
    private val hireStarterWork: HireStarterWorkApi,
    private val hireStarterWorkActions: HireStarterWorkActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PERSON
    override val gerund = "record where a chat placed you"

    /**
     * Offers to record where the conversation placed the hire on one competency, without writing.
     *
     * Both halves are resolved *here*, not at confirm time, and that is the point of doing it
     * before the button rather than after it. A key nothing matches or a level that is not one of
     * the four words comes back as a correction the mentor can act on mid-conversation; discovered
     * at confirm time it would instead be a hire clicking a button and being told no.
     *
     * The button names the competency and the level rather than saying "save this". A hire is
     * confirming a judgement about their own skill, and the label is the last thing they read
     * before it becomes a record.
     */
    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val key = call.textArgument("competency_key")
        val label = key.takeIf { it.isNotBlank() }?.let { hireStarterWork.competencyLabel(it) }
            ?: return HireActionOutcome(
                "No competency matched “$key”. Read get_competencies_to_assess and pass a " +
                    "competency_key exactly as it appears there.",
                null,
            )

        val level = ProficiencyLevel.fromWord(call.textArgument("level"))
            ?: return HireActionOutcome(
                "That is not a level I can record. Use one of: ${ProficiencyLevel.WORDS.joinToString(", ")}.",
                null,
            )

        return HireActionOutcome(
            toolResult = "Proposed to the hire: place them at ${level.word} on “$label”. They will " +
                "see a confirm button; nothing is recorded unless they click it. Tell them what you " +
                "concluded and why — do not claim it is saved.",
            proposal = HireActionProposal(
                action = RECORD_ASSESSMENT,
                label = "Save: $label — ${level.word}",
                question = null,
                competencyKey = key,
                level = level.word,
            ),
        )
    }

    /**
     * Writes the placement the hire confirmed.
     *
     * The level is re-read from the word here rather than trusted as a rank, so a client cannot
     * confirm a level the scale does not have. [HireStarterWorkActionsApi] owns every rule about
     * what a placement may overwrite; this only relays the sentence it produced.
     */
    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val competencyKey = request.competencyKey
        if (competencyKey.isNullOrBlank()) {
            return BuddyActionResponse(ok = false, message = "No skill was proposed to record.")
        }
        val resolvedLevel = request.level?.let { ProficiencyLevel.fromWord(it) }
            ?: return BuddyActionResponse(ok = false, message = "No level was proposed to record.")

        val outcome = hireStarterWorkActions.recordPlacement(context.hire, competencyKey, resolvedLevel)
        return BuddyActionResponse(ok = outcome.recorded, message = outcome.message)
    }

    companion object {
        const val RECORD_ASSESSMENT = "record_assessment"

        val SPEC = BuddyToolSpecDto(
            name = RECORD_ASSESSMENT,
            description = "Offer to record where this conversation placed the hire on ONE " +
                "competency. Call it as you finish talking about each skill, not once at the end. " +
                "Only ever after actually asking them something about it: a placement nobody was " +
                "asked about is a guess in a permanent record. Pass the competency_key exactly as " +
                "get_competencies_to_assess gave it, and a level of " +
                "${ProficiencyLevel.WORDS.joinToString("/")} — beginner is the honest answer when " +
                "they have seen it but not used it. This does NOT record anything by itself; the " +
                "hire sees a confirm button naming the skill and the level, and only they can " +
                "save it. Tell them what you concluded and why before you offer it, and take a " +
                "correction as the answer — it is their skill, not your verdict. What they say " +
                "here is a starting point that their accepted work will outrank later, so never " +
                "present it as final or as a score.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("competency_key") {
                        put("type", "string")
                        put("description", "The competency_key from get_competencies_to_assess.")
                    }
                    putJsonObject("level") {
                        put("type", "string")
                        put(
                            "description",
                            "Where the conversation placed them: ${ProficiencyLevel.WORDS.joinToString(", ")}.",
                        )
                    }
                }
                putJsonArray("required") {
                    add("competency_key")
                    add("level")
                }
            },
        )
    }
}
