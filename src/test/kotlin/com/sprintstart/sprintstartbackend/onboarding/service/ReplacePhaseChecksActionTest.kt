package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.UpdatePhaseQuestionsRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetPhaseQuestionsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionForAdminResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionOptionForAdminResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ReplacePhaseChecksActionTest {
    private val f = ContentFixture()
    private val questionService: QuestionAttemptService = mockk(relaxed = true)
    private val action = ReplacePhaseChecksAction(f.scope, questionService)
    private val phaseId = UUID.randomUUID()

    private val shortId = UUID.randomUUID()
    private val choiceId = UUID.randomUUID()
    private val optionA = UUID.randomUUID()
    private val optionB = UUID.randomUUID()

    /** The phase as it stands: one short-text question and one multiple-choice one. */
    private fun existing() {
        f.element(PathElementKind.PHASE, phaseId, title = "Setup")
        every { questionService.getPhaseQuestions(phaseId) } returns
            GetPhaseQuestionsResponse(
                phaseId,
                listOf(
                    QuestionForAdminResponse(
                        shortId,
                        0,
                        CheckQuestionType.SHORT_TEXT,
                        "Which command builds it?",
                        null,
                        "make",
                    ),
                    QuestionForAdminResponse(
                        id = choiceId,
                        position = 1,
                        type = CheckQuestionType.MULTIPLE_CHOICE,
                        question = "Where do logs go?",
                        explanation = "See the runbook",
                        options = listOf(
                            QuestionOptionForAdminResponse(optionA, 0, "stdout", true),
                            QuestionOptionForAdminResponse(optionB, 1, "a file", false),
                        ),
                    ),
                ),
            )
    }

    private fun short(id: UUID? = null, text: String = "Which command builds it?", answer: String? = "make") =
        f.json(
            *listOfNotNull(
                id?.let { "id" to it },
                "type" to "SHORT_TEXT",
                "question" to text,
                answer?.let {
                    "correct_answer" to
                        it
                },
            ).toTypedArray(),
        )

    private fun choice(id: UUID? = null, options: List<JsonObject>) =
        f.json(
            *listOfNotNull(
                id?.let { "id" to it },
                "type" to "MULTIPLE_CHOICE",
                "question" to "Where do logs go?",
                "options" to JsonArray(options),
            ).toTypedArray(),
        )

    private fun option(id: UUID? = null, label: String, correct: Boolean) =
        f.json(*listOfNotNull(id?.let { "id" to it }, "label" to label, "correct" to correct).toTypedArray())

    private fun replace(vararg questions: JsonObject) =
        f.call("replace_phase_checks", "phase_id" to phaseId, "questions" to JsonArray(questions.toList()))

    @Test
    fun `previews what is kept, what is new and what is deleted`() {
        existing()

        val draft = f.proposed(
            action.draft(
                replace(short(shortId), short(text = "How do you run the tests?", answer = "make test")),
                f.context,
            ),
        )

        assertThat(draft.preview).contains(
            "It will have 2 questions",
            "1. (kept) short text: Which command builds it?",
            "2. (new) short text: How do you run the tests?",
            "Correct answer: make test",
            "These are deleted, with everybody's past answers to them:",
            "- Where do logs go?",
            "no longer waits for it",
        )
    }

    @Test
    fun `keeping everything deletes nothing and says nothing about deletion`() {
        existing()

        val draft = f.proposed(
            action.draft(
                replace(
                    short(shortId),
                    choice(choiceId, listOf(option(optionA, "stdout", true), option(optionB, "a file", false))),
                ),
                f.context,
            ),
        )

        assertThat(draft.preview).doesNotContain("deleted")
    }

    @Test
    fun `an empty list removes them all, and says so`() {
        existing()

        val draft = f.proposed(action.draft(replace(), f.context))

        assertThat(draft.preview).contains("It will have 0 questions", "Which command builds it?", "Where do logs go?")
    }

    @Test
    fun `an empty list for a phase with no questions is refused as no change`() {
        f.element(PathElementKind.PHASE, phaseId)
        every { questionService.getPhaseQuestions(phaseId) } returns GetPhaseQuestionsResponse(phaseId, emptyList())

        assertThat(f.refusal(action.draft(replace(), f.context))).contains("nothing would change")
    }

    @Test
    fun `a call that leaves out the list is refused rather than read as clear-all`() {
        existing()

        val reason = f.refusal(action.draft(f.call("replace_phase_checks", "phase_id" to phaseId), f.context))

        assertThat(reason).contains("complete list")
    }

    @Test
    fun `an id that is not one of the phase's questions is refused`() {
        existing()

        val reason = f.refusal(action.draft(replace(short(UUID.randomUUID())), f.context))

        assertThat(reason).contains("Question 1", "not one of this phase's questions")
    }

    @Test
    fun `an option id that is not one of the question's options is refused`() {
        existing()

        val reason = f.refusal(
            action.draft(
                replace(choice(choiceId, listOf(option(UUID.randomUUID(), "x", true), option(optionB, "y", false)))),
                f.context,
            ),
        )

        assertThat(reason).contains("option id that is not one of that question's options")
    }

    @Test
    fun `the same question listed twice is refused`() {
        existing()

        assertThat(f.refusal(action.draft(replace(short(shortId), short(shortId)), f.context)))
            .contains("listed twice")
    }

    @Test
    fun `questions the service would refuse are refused at proposal`() {
        existing()

        assertThat(f.refusal(action.draft(replace(short(answer = null)), f.context))).contains("needs a correct_answer")
        assertThat(f.refusal(action.draft(replace(short(text = " ")), f.context))).contains("has no text")
        assertThat(
            f.refusal(
                action.draft(replace(choice(options = listOf(option(label = "one", correct = true)))), f.context),
            ),
        ).contains("at least two options")
        assertThat(
            f.refusal(
                action.draft(
                    replace(
                        choice(
                            options = listOf(
                                option(label = "a", correct = false),
                                option(label = "b", correct = false),
                            ),
                        ),
                    ),
                    f.context,
                ),
            ),
        ).contains("at least one correct option")
    }

    @Test
    fun `an unknown question type is refused, naming the ones there are`() {
        existing()
        val odd = f.json("type" to "ESSAY", "question" to "Discuss")

        assertThat(f.refusal(action.draft(replace(odd), f.context))).contains("MULTIPLE_CHOICE", "SHORT_TEXT")
    }

    @Test
    fun `a phase on somebody else's path is refused`() {
        f.element(PathElementKind.PHASE, phaseId, owner = f.outsiderId)

        assertThat(f.refusal(action.draft(replace(short()), f.context))).contains("not on the onboarding path")
    }

    @Test
    fun `nothing is written while drafting`() {
        existing()

        action.draft(replace(short(shortId)), f.context)

        verify(exactly = 0) { questionService.replacePhaseQuestions(any(), any()) }
    }

    @Test
    fun `a confirm is turned down when the phase's questions changed since the preview`() {
        existing()
        val proposed = f.proposed(action.draft(replace(short(shortId)), f.context))

        every { questionService.getPhaseQuestions(phaseId) } returns
            GetPhaseQuestionsResponse(
                phaseId,
                listOf(
                    QuestionForAdminResponse(
                        UUID.randomUUID(),
                        0,
                        CheckQuestionType.SHORT_TEXT,
                        "Added since",
                        null,
                        "x",
                    ),
                ),
            )

        assertThat(action.recheck(proposed.params, f.context)).contains("changed since this was proposed")
    }

    @Test
    fun `a confirm with the phase as it was passes`() {
        existing()
        val proposed = f.proposed(action.draft(replace(short(shortId)), f.context))

        assertThat(action.recheck(proposed.params, f.context)).isNull()
    }

    @Test
    fun `performing sends the whole list, keeping ids and numbering places from the list order`() =
        runTest {
            existing()
            val proposed = f.proposed(
                action.draft(
                    replace(
                        short(text = "New first"),
                        choice(
                            choiceId,
                            listOf(option(optionA, "stdout", true), option(label = "syslog", correct = false)),
                        ),
                    ),
                    f.context,
                ),
            )
            val request = slot<UpdatePhaseQuestionsRequest>()
            every { questionService.replacePhaseQuestions(phaseId, capture(request)) } returns mockk(relaxed = true)

            action.perform(proposed.params, f.context)

            val sent = request.captured.questions
            assertThat(sent.map { it.position }).containsExactly(0, 1)
            assertThat(sent[0].id).isNull()
            assertThat(sent[1].id).isEqualTo(choiceId)
            assertThat(sent[1].options.map { it.id }).containsExactly(optionA, null)
            assertThat(sent[1].options.map { it.correct }).containsExactly(true, false)
        }
}
