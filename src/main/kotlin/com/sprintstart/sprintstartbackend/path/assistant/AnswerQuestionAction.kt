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
import com.sprintstart.sprintstartbackend.blueprint.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.path.external.HirePathActionsApi
import com.sprintstart.sprintstartbackend.path.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.path.model.request.question.SubmitQuestionAttemptRequest
import com.sprintstart.sprintstartbackend.path.model.response.question.GetOnboardingQuestionForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.question.QuestionOptionForUserResponse
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.CheckQuestionId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * The buddy's knowledge-check action: offering to send the hire's own answer to a question on
 * their path, and sending it once they confirm.
 *
 * The hire's words, never the mentor's. The mentor is not told which option is correct (see
 * [MyPathTool]), so it cannot answer for them even if it tried — and the button shows the answer that
 * will be sent, because an attempt is recorded whether it is right or not.
 *
 * Like every path action it writes to the hire's own attempts, never to the question.
 */
@Component
@Order(HireActionOrder.ANSWER_QUESTION)
class AnswerQuestionAction(
    private val myPathTool: MyPathTool,
    private val hirePathActionsApi: HirePathActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PERSON
    override val gerund = "send an answer to a question"

    override fun isMountedFor(hire: UserId): Boolean = myPathTool.hasPath(hire.value)

    /**
     * Offers to send the hire's own answer to a knowledge question.
     *
     * ### Why the mentor passes words and not option ids
     *
     * The mentor is not told which option is correct (see [MyPathTool]), so what it passes here
     * is what the hire said — "the retro", or "Git" and "Docker" as two picks — and the match to
     * options happens server-side in [matchOption]. An option id from the mentor would be the mentor
     * choosing, and the point is that the hire chooses.
     *
     * ### Why the proposal carries option ids anyway
     *
     * Once matched, the *resolved* options go on the proposal next to the labels the button shows,
     * and the confirm checks those instead of matching the words again. A PM can rename options
     * without changing their ids, and a re-match after that could land on an option the hire never
     * read.
     *
     * Multiple choice takes a set: an attempt is graded on the exact set picked, so a question with
     * two right options can only be passed by sending both in one attempt.
     *
     * A match that cannot be made comes back naming the options, so the mentor asks again instead of
     * guessing on the hire's behalf.
     */
    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val userId = context.hire
        val questionId = call.uuidArgument("question_id")
            ?: return notProposed(
                "No question_id was provided. Read get_my_onboarding_path and pass the question_id " +
                    "of the question they are answering.",
            )
        val question = myPathTool.findQuestion(userId.value, questionId)
            ?: return notProposed(
                "No question of this hire's own path has that id. Read get_my_onboarding_path again.",
            )
        val answer = call.textArgument("answer")
        val isChoice = question.type == CheckQuestionType.MULTIPLE_CHOICE
        // Each option the hire picked, one per entry. A lone `answer` is one pick.
        val picks = call
            .lenientTextListArgument(
                "selected_options",
            ).ifEmpty { listOf(answer) }
            .filter { it.isNotBlank() }

        val refusal = when {
            (if (isChoice) picks.isEmpty() else answer.isBlank()) ->
                "No answer was provided. This has to be the hire's own answer in their own words — " +
                    "ask them what they want to send, and never answer it for them."
            question.status == QuestionStatus.PASSED ->
                "They have already passed “${question.question}”, so there is nothing to send. Tell " +
                    "them it is behind them."
            question.status == QuestionStatus.LOCKED ->
                "“${question.question}” is locked: something it waits on is not finished yet. Say " +
                    "what it is waiting on rather than offering to answer it."
            else -> null
        }
        if (refusal != null) return notProposed(refusal)

        val chosen = if (isChoice) {
            val matched = picks
                .map { pick ->
                    matchOption(question, pick)
                        ?: return notProposed(
                            "“$pick” does not match exactly one of the options for that question. The " +
                                "options are: ${question.options.joinToString("; ") { it.label }}. Ask the " +
                                "hire which of them they mean and pass that back — if they picked more than " +
                                "one, pass each as its own entry in selected_options.",
                        )
                }.toSet()
            // In the question's own order, so the button reads the way the options are listed.
            question.options.filter { it in matched }
        } else {
            emptyList()
        }
        val shown = if (isChoice) shownChoice(chosen) else answer

        return HireActionOutcome(
            toolResult = "Proposed to the hire: send “$shown” as their answer to " +
                "“${question.question}”. They see a confirm button showing that answer, and nothing " +
                "is recorded unless they click it. An attempt is kept whether it is right or not, so " +
                "make sure it is the answer they meant — and do not tell them whether it is correct, " +
                "because you have not been told either.",
            proposal = HireActionProposal(
                action = ANSWER_QUESTION,
                label = "Send this answer: “${shown.take(ANSWER_LABEL_LIMIT)}”",
                question = null,
                questionId = question.id,
                answer = shown,
                optionIds = chosen.map { it.id },
            ),
        )
    }

    /**
     * Records the confirmed answer, and relays what came back.
     *
     * `ok` is true for a wrong answer too, and that is not an oversight: the action was to *send*
     * their answer, and it was sent. Whether it was right is the message's job — a wrong attempt
     * shown as a failed action would read as the buddy having broken rather than as the hire having
     * missed, on the one surface where missing is supposed to be ordinary.
     */
    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val userId = context.hire
        val questionId = request.questionId
        val answer = request.answer
        if (questionId == null || answer.isNullOrBlank()) {
            return BuddyActionResponse(ok = false, message = "No answer was proposed to send.")
        }
        val question = myPathTool.findQuestion(userId.value, questionId)
            ?: return BuddyActionResponse(ok = false, message = "That question isn't on your path.")

        // Checked again at confirm time, for the same reason completing a step checks its lock: the
        // attempt route grades whatever it is sent, and a button left in the conversation can be
        // clicked after the hire already passed the question on their page. A second attempt there
        // would be kept for no reason, and a wrong one would read as having lost the pass.
        when (question.status) {
            QuestionStatus.PASSED ->
                return BuddyActionResponse(ok = false, message = "You've already passed that one — nothing was sent.")
            QuestionStatus.LOCKED ->
                return BuddyActionResponse(
                    ok = false,
                    message = "That question is locked right now — something it waits on isn't finished yet.",
                )
            else -> Unit
        }

        val submission = if (question.type == CheckQuestionType.MULTIPLE_CHOICE) {
            // The options the button stood for, still there and still reading the way it showed
            // them. Anything else is a proposal about a question that has since changed.
            val byId = question.options.associateBy { it.id }
            val chosen = request.optionIds.mapNotNull { byId[it] }
            if (chosen.isEmpty() || chosen.size != request.optionIds.size || shownChoice(chosen) != answer) {
                return BuddyActionResponse(
                    ok = false,
                    message = "That question changed since this was suggested, so nothing was sent — ask me again.",
                )
            }
            SubmitQuestionAttemptRequest(selectedOptionIds = chosen.map { it.id }.distinct())
        } else {
            SubmitQuestionAttemptRequest(textAnswer = answer)
        }

        val result = hirePathActionsApi.answerCheck(authId, CheckQuestionId(questionId), submission)
        // The submit response is the one user-facing place correct answers are revealed, and the
        // hire's own page shows them there. Relaying the same thing keeps the two surfaces telling
        // one story; withholding it here would make the conversation the worse place to answer.
        val learning = listOfNotNull(result.feedback, result.explanation).joinToString(" ")
        return BuddyActionResponse(
            ok = true,
            message = if (result.correct) {
                "That was right — the question is passed. $learning".trim()
            } else {
                "Not quite. It stays open, so you can try again whenever you like. $learning".trim()
            },
        )
    }

    /**
     * Which option the hire meant, or null when that is not clear enough to act on.
     *
     * Three passes, loosening in a fixed order: the label exactly, then a label containing what they
     * said, then what they said containing a label. Each pass only counts when it matches *one*
     * option — "the first one" against two options that start alike is ambiguous, and guessing there
     * would record an answer the hire did not give.
     *
     * Only called when proposing. The confirm checks the ids the proposal resolved instead of
     * matching again (see [proposeAnswer]).
     */
    private fun matchOption(
        question: GetOnboardingQuestionForUserResponse,
        answer: String,
    ): QuestionOptionForUserResponse? {
        val needle = answer.trim()
        if (needle.isBlank()) return null

        val exact = question.options.filter { it.label.trim().equals(needle, ignoreCase = true) }
        if (exact.size == 1) return exact.single()

        val labelContains = question.options.filter { it.label.contains(needle, ignoreCase = true) }
        if (labelContains.size == 1) return labelContains.single()

        val answerContains = question.options.filter { needle.contains(it.label.trim(), ignoreCase = true) }
        return answerContains.singleOrNull()
    }

    /** What the confirm button shows for a set of options, and what the confirm checks it against. */
    private fun shownChoice(options: List<QuestionOptionForUserResponse>): String =
        options.joinToString(", ") { it.label.trim() }

    companion object {
        const val ANSWER_QUESTION = "answer_question"

        /** How many characters of an answer the confirm button shows before it is cut. */
        private const val ANSWER_LABEL_LIMIT = 60

        val SPEC = BuddyToolSpecDto(
            name = ANSWER_QUESTION,
            description = "Offer to send the hire's answer to one knowledge-check question on their " +
                "path. Read get_my_onboarding_path for the question_id and the options they are " +
                "looking at. Pass THEIR answer, in their words — for a multiple-choice question, " +
                "whichever option they picked; it is matched to an option for you, and an answer " +
                "that matches none comes back so you can ask again. If they picked MORE THAN ONE " +
                "option, pass every one of them in selected_options: an attempt is graded on the " +
                "whole set, so sending one at a time can never pass. This does NOT record anything " +
                "by itself; they see a confirm button showing the answer that will be sent. An " +
                "attempt is kept whether it is right or wrong. You are a tutor here, not an " +
                "examiner and not a shortcut: explain the material the question is about, from the " +
                "project's own documents, and let them answer it. You are not told which answer is " +
                "correct — so never state one, never hint at one, and never pass an answer they did " +
                "not give. If they ask you to just tell them, say plainly that you do not know it " +
                "and offer to go through the material instead.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("question_id") {
                        put("type", "string")
                        put("description", "The question_id from get_my_onboarding_path.")
                    }
                    putJsonObject("answer") {
                        put("type", "string")
                        put(
                            "description",
                            "The hire's own answer, in their own words. For multiple choice, the " +
                                "option they picked — the label, or enough of it to identify it.",
                        )
                    }
                    putJsonObject("selected_options") {
                        put("type", "array")
                        putJsonObject("items") { put("type", "string") }
                        put(
                            "description",
                            "Multiple choice only, when they picked more than one option: each one " +
                                "as its own entry, the label or enough of it to identify it. Takes " +
                                "the place of answer.",
                        )
                    }
                }
                putJsonArray("required") {
                    add("question_id")
                }
            },
        )
    }
}
