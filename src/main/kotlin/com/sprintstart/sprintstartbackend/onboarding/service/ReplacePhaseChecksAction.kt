package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.UpdateOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.UpdatePhaseQuestionsRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.UpdateQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionForAdminResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/*
 * Replacing a phase's knowledge checks.
 *
 * The service takes the whole list: a question it is given with a known id is edited in place, one
 * without an id is created, and one it is *not* given is deleted — after which the hires' answers to it,
 * which are kept only by its id, count for nothing, and no step waits on it any more. So the action
 * takes the whole list too, checks every id in it against what the phase has, and previews what stays,
 * what is new and what goes.
 */

private const val CHECKS_CHANGED =
    "The phase's questions changed since this was proposed, so nothing was changed. Call get_phase_checks " +
        "and offer it again."

private val QUESTION_TYPES = CheckQuestionType.entries.map { it.name }

/** What reading the model's questions made of them. */
private sealed interface Checks {
    data class Valid(
        val questions: List<UpdateQuestionRequest>,
    ) : Checks

    data class Invalid(
        val reason: String,
    ) : Checks
}

private fun questionTypeOf(text: String): CheckQuestionType? =
    CheckQuestionType.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }

/** The reason [question] cannot be stored as asked, or null. Mirrors what the service refuses at the write. */
private fun problemWith(question: UpdateQuestionRequest, number: Int): String? =
    when {
        question.question.isBlank() -> "Question $number has no text."
        question.type == CheckQuestionType.SHORT_TEXT && question.correctAnswer.isNullOrBlank() ->
            "Question $number is a short-text question and needs a correct_answer."
        question.type == CheckQuestionType.MULTIPLE_CHOICE && question.options.size < 2 ->
            "Question $number is multiple choice and needs at least two options."
        question.type == CheckQuestionType.MULTIPLE_CHOICE && question.options.none { it.correct } ->
            "Question $number is multiple choice and needs at least one correct option."
        question.options.any { it.label.isBlank() } -> "Question $number has an option with no text."
        else -> null
    }

private fun readOption(raw: JsonObject, index: Int) =
    UpdateOptionRequest(
        id = raw.uuid("id"),
        position = index,
        label = raw.text("label"),
        correct = raw.boolean("correct") == true,
    )

/** One question as the model wrote it, or the reason it cannot be read; position is its place in the list. */
private fun readQuestion(raw: JsonObject, index: Int): Pair<UpdateQuestionRequest?, String?> {
    val type = questionTypeOf(raw.text("type"))
        ?: return null to "Question ${index + 1} needs a type: ${QUESTION_TYPES.joinToString(" or ")}."
    if (raw.text("id").isNotEmpty() && raw.uuid("id") == null) {
        return null to "Question ${index + 1} has an id that is not one from get_phase_checks."
    }
    return UpdateQuestionRequest(
        id = raw.uuid("id"),
        position = index,
        type = type,
        question = raw.text("question"),
        explanation = raw.text("explanation").takeIf { it.isNotEmpty() },
        correctAnswer = raw.text("correct_answer").takeIf { type == CheckQuestionType.SHORT_TEXT && it.isNotEmpty() },
        options = if (type == CheckQuestionType.MULTIPLE_CHOICE) {
            raw.objectArray("options").mapIndexed { i, option -> readOption(option, i) }
        } else {
            emptyList()
        },
    ) to null
}

/** Why an id in [question] is not one the phase has, so nothing is silently created or lost; null when all are. */
private fun unknownId(question: UpdateQuestionRequest, current: List<QuestionForAdminResponse>, number: Int): String? {
    val id = question.id ?: return null
    val existing = current.firstOrNull { it.id == id }
        ?: return "Question $number has an id that is not one of this phase's questions. Call get_phase_checks " +
            "and use its ids, or leave the id out for a new question."
    val known = existing.options.map { it.id }.toSet()
    return "Question $number has an option id that is not one of that question's options."
        .takeIf { question.options.any { it.id != null && it.id !in known } }
}

private fun readChecks(raw: List<JsonObject>, current: List<QuestionForAdminResponse>): Checks {
    val questions = mutableListOf<UpdateQuestionRequest>()
    raw.forEachIndexed { index, item ->
        val (question, unreadable) = readQuestion(item, index)
        val problem = unreadable ?: question?.let { problemWith(it, index + 1) ?: unknownId(it, current, index + 1) }
        if (problem != null || question == null) return Checks.Invalid(problem.orEmpty())
        questions += question
    }
    val ids = questions.mapNotNull { it.id }
    return if (ids.size != ids.toSet().size) {
        Checks.Invalid("The same question id is listed twice.")
    } else {
        Checks.Valid(questions)
    }
}

/** The stored form of a valid list: the same shape the model sent, so a confirm reads it the same way. */
private fun UpdateQuestionRequest.stored(): JsonObject =
    buildJsonObject {
        id?.let { put("id", it.toString()) }
        put("type", type.name)
        put("question", question)
        explanation?.let { put("explanation", it) }
        correctAnswer?.let { put("correct_answer", it) }
        if (options.isNotEmpty()) {
            putJsonArray("options") {
                options.forEach { option ->
                    add(
                        buildJsonObject {
                            option.id?.let { put("id", it.toString()) }
                            put("label", option.label)
                            put("correct", option.correct)
                        },
                    )
                }
            }
        }
    }

private fun UpdateQuestionRequest.described(number: Int, kept: Boolean): String =
    buildString {
        append("$number. ${if (kept) "(kept) " else "(new) "}${type.name.lowercase().replace('_', ' ')}: $question")
        correctAnswer?.let { append("\n   Correct answer: $it") }
        options.forEach { append("\n   ${if (it.correct) "[correct]" else "[wrong]  "} ${it.label}") }
        explanation?.let { append("\n   Explanation: $it") }
    }

/** Offers to replace the knowledge-check questions of a phase on a member's path. */
@Component
class ReplacePhaseChecksAction(
    private val scope: ContentScope,
    private val questionAttemptService: QuestionAttemptService,
) : TeamActionHandler {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "replace_phase_checks",
        description = "Offer to replace the knowledge-check questions at the end of a phase on a member's " +
            "onboarding path. It takes the WHOLE new list: a question with its id is edited in place, one " +
            "without an id is new, and any current question left out is deleted and the hires' answers to " +
            "it stop counting. Always call get_phase_checks first and start from what it shows. This does NOT change " +
            "anything by itself; the manager confirms.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("phase_id") {
                    put("type", "string")
                    put("description", "The phase_id from get_member_path.")
                }
                putJsonObject("questions") {
                    put("type", "array")
                    put("description", "The complete list of questions, in order. An empty list removes them all.")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("id") {
                                put("type", "string")
                                put("description", "The question's id from get_phase_checks; leave out for a new one.")
                            }
                            putJsonObject("type") {
                                put("type", "string")
                                putJsonArray("enum") { QUESTION_TYPES.forEach { add(it) } }
                            }
                            putJsonObject("question") { put("type", "string") }
                            putJsonObject("explanation") {
                                put("type", "string")
                                put("description", "Optional. Shown to the hire after they answer.")
                            }
                            putJsonObject("correct_answer") {
                                put("type", "string")
                                put("description", "SHORT_TEXT only: the answer that counts as right.")
                            }
                            putJsonObject("options") {
                                put("type", "array")
                                put("description", "MULTIPLE_CHOICE only: at least two, at least one correct.")
                                putJsonObject("items") {
                                    put("type", "object")
                                    putJsonObject("properties") {
                                        putJsonObject("id") { put("type", "string") }
                                        putJsonObject("label") { put("type", "string") }
                                        putJsonObject("correct") { put("type", "boolean") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            putJsonArray("required") {
                add("phase_id")
                add("questions")
            }
        },
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val target = scope.element(PathElementKind.PHASE, call.uuidArgument("phase_id"), context.projectId)
            ?: return TeamActionDraft.Refused(notInScope(PathElementKind.PHASE))
        if (call.arguments["questions"] !is JsonArray) {
            return TeamActionDraft.Refused(
                "Pass questions as the complete list, even an empty one. Call get_phase_checks to see the " +
                    "current ones.",
            )
        }
        val current = questionAttemptService.getPhaseQuestions(target.element.id).questions
        val questions = when (val read = readChecks(call.arguments.objectArray("questions"), current)) {
            is Checks.Invalid -> return TeamActionDraft.Refused(read.reason)
            is Checks.Valid -> read.questions
        }
        if (questions.isEmpty() && current.isEmpty()) {
            return TeamActionDraft.Refused("The phase has no questions and none were given, so nothing would change.")
        }

        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("phase_id", target.element.id.toString())
                // What the phase had when this was written, so a confirm can tell it changed since.
                putJsonArray("base_ids") { current.forEach { add(it.id.toString()) } }
                putJsonArray("questions") { questions.forEach { add(it.stored()) } }
            },
            label = "Replace the checks of “${target.element.title.forLabel()}”",
            preview = previewOf(target, questions, current, scope.sharedNote(target.owner, context.projectId)),
        )
    }

    private fun previewOf(
        target: ScopedElement,
        questions: List<UpdateQuestionRequest>,
        current: List<QuestionForAdminResponse>,
        sharedNote: String,
    ): String =
        buildString {
            appendLine(
                "Replace the knowledge checks of “${target.element.title}” on ${target.owner.displayName}'s path.",
            )
            appendLine("It will have ${questions.size} question${if (questions.size == 1) "" else "s"}:")
            questions.forEachIndexed { i, q -> appendLine(q.described(i + 1, kept = q.id != null)) }
            val removed = current.filter { existing -> questions.none { it.id == existing.id } }
            if (removed.isNotEmpty()) {
                appendLine()
                appendLine("These are deleted, and nobody's past answers to them count any more:")
                removed.forEach { appendLine("- ${it.question}") }
                appendLine("A step that was waiting on one of them no longer waits for it.")
            }
            if (sharedNote.isNotEmpty()) append("\n$sharedNote")
        }.trim()

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val phaseId = params.uuid("phase_id")
        scope.missing(PathElementKind.PHASE, phaseId, context.projectId)?.let { return it }
        val base = (params["base_ids"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
        val now = questionAttemptService.getPhaseQuestions(requireNotNull(phaseId)).questions.map { it.id.toString() }
        return CHECKS_CHANGED.takeIf { now.toSet() != base }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val phaseId: UUID = requireNotNull(params.uuid("phase_id"))
        val current = questionAttemptService.getPhaseQuestions(phaseId).questions
        val questions = when (val read = readChecks(params.objectArray("questions"), current)) {
            is Checks.Invalid -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, read.reason)
            is Checks.Valid -> read.questions
        }
        questionAttemptService.replacePhaseQuestions(phaseId, UpdatePhaseQuestionsRequest(questions))
        return "Done. The phase now has ${questions.size} question${if (questions.size == 1) "" else "s"}."
    }
}
