package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationOrigin
import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationStep
import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetAdminOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.orientation.MyOrientationResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.orientation.OrientationCitationResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.orientation.OrientationPacketResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.orientation.OrientationSectionResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetPhaseQuestionsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionForAdminResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionOptionForAdminResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetOnboardingSkipResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** The reads of the content area that are about what waits for the manager, rather than the path itself. */
class ContentTeamToolsReadsTest {
    private val f = ContentFixture()
    private val skipService: OnboardingSkipService = mockk(relaxed = true)
    private val feedbackService: OnboardingFeedbackService = mockk(relaxed = true)
    private val questionService: QuestionAttemptService = mockk(relaxed = true)
    private val orientationService: TaskOrientationService = mockk(relaxed = true)
    private val tools = ContentTeamTools(
        f.scope,
        f.pathElements,
        mockk(relaxed = true),
        mockk(relaxed = true),
        skipService,
        feedbackService,
        questionService,
        orientationService,
    )

    private fun read(name: String, vararg args: Pair<String, Any?>) = tools.execute(f.call(name, *args), f.context)

    @Test
    fun `pending skips list only requests still waiting, from members of this project`() {
        val stepId = UUID.randomUUID()
        f.element(PathElementKind.STEP, stepId, title = "Install", stepStatus = StepStatus.IN_PROGRESS)
        every { skipService.getAllSkipsByUserId(f.memberId) } returns
            listOf(
                GetOnboardingSkipResponse(
                    UUID.randomUUID(),
                    stepId,
                    SkipStatus.PENDING,
                    "I know it",
                    createdAt = Instant.parse("2026-09-01T10:00:00Z"),
                ),
                GetOnboardingSkipResponse(UUID.randomUUID(), stepId, SkipStatus.ACCEPTED, "old one"),
            )

        val text = read("list_pending_skips")

        assertThat(text).contains(
            "Sam Rivera asks to skip “Install” (in progress)",
            "[skip_id:",
            "“I know it”",
            "2026-09-01",
        )
        assertThat(text).doesNotContain("old one")
    }

    @Test
    fun `no pending skips is said plainly`() {
        every { skipService.getAllSkipsByUserId(f.memberId) } returns emptyList()

        assertThat(read("list_pending_skips")).contains("skip request waiting")
    }

    private fun feedbackOf(read: Boolean, message: String) =
        GetAdminOnboardingFeedbackResponse(
            id = UUID.randomUUID(),
            userId = f.memberId,
            stepId = UUID.randomUUID(),
            stepTitle = "Install",
            message = message,
            read = read,
            createdAt = Instant.parse("2026-09-02T10:00:00Z"),
        )

    @Test
    fun `feedback shows unread by default and read only on request`() {
        every { feedbackService.getAllFeedbackByUserId(f.memberId) } returns
            listOf(feedbackOf(read = false, message = "Needed Docker"), feedbackOf(read = true, message = "Fine"))

        val unread = read("list_feedback")
        val everything = read("list_feedback", "include_read" to true)

        assertThat(unread).contains("Sam Rivera on “Install”", "[feedback_id:", "“Needed Docker”")
        assertThat(unread).doesNotContain("Fine")
        assertThat(everything).contains("“Needed Docker”", "“Fine”", "(read)")
    }

    @Test
    fun `when everything is read the answer says how many there are`() {
        every { feedbackService.getAllFeedbackByUserId(f.memberId) } returns
            listOf(feedbackOf(read = true, message = "Fine"))

        assertThat(read("list_feedback")).contains("no unread feedback", "already read")
    }

    @Test
    fun `phase checks show the ids and correct answers a replacement starts from`() {
        val phaseId = UUID.randomUUID()
        f.element(PathElementKind.PHASE, phaseId, title = "Setup")
        val questionId = UUID.randomUUID()
        val optionId = UUID.randomUUID()
        every { questionService.getPhaseQuestions(phaseId) } returns
            GetPhaseQuestionsResponse(
                phaseId,
                listOf(
                    QuestionForAdminResponse(
                        id = questionId,
                        position = 0,
                        type = CheckQuestionType.MULTIPLE_CHOICE,
                        question = "Where do logs go?",
                        explanation = "See runbook",
                        options = listOf(QuestionOptionForAdminResponse(optionId, 0, "stdout", true)),
                    ),
                ),
            )

        val text = read("get_phase_checks", "phase_id" to phaseId)

        assertThat(text).contains("Setup", "Sam Rivera", "[question_id: $questionId]", "See runbook")
        assertThat(text).contains("[correct] stdout [option_id: $optionId]")
    }

    @Test
    fun `phase checks of a phase on another person's path are not shown`() {
        val phaseId = UUID.randomUUID()
        f.element(PathElementKind.PHASE, phaseId, owner = f.outsiderId)

        val text = read("get_phase_checks", "phase_id" to phaseId)

        assertThat(text).contains("not on the onboarding path")
        verify(exactly = 0) { questionService.getPhaseQuestions(any()) }
    }

    @Test
    fun `an orientation packet is shown in full, and says who wrote it`() {
        val task = f.task()
        val citation = OrientationCitationResponse("README.md", "c1", "https://x.io/readme")
        every { orientationService.getForAuthoring(task.id, f.projectId) } returns
            MyOrientationResponse(
                task.id,
                task.title,
                null,
                OrientationPacketResponse(
                    taskId = task.id,
                    taskTitle = task.title,
                    summary = "Start here",
                    sections = listOf(
                        OrientationSectionResponse(
                            OrientationStep.SET_UP,
                            "Install",
                            "Run make setup.",
                            listOf(citation),
                        ),
                    ),
                    sources = emptyList(),
                    assembledAt = Instant.now(),
                    origin = OrientationOrigin.HUMAN,
                ),
                null,
            )

        val text = read("get_orientation_packet", "task_id" to task.id)

        assertThat(text).contains("written by a person", "Summary: Start here", "set up — Install", "Run make setup.")
        assertThat(text).contains("README.md <https://x.io/readme>")
    }

    @Test
    fun `a task with no packet says so, and one from an unlinked repository is refused`() {
        val task = f.task()
        every { orientationService.getForAuthoring(task.id, f.projectId) } returns
            MyOrientationResponse(task.id, task.title, null, null, null)
        val unlinked = f.task(linked = false)

        assertThat(read("get_orientation_packet", "task_id" to task.id)).contains("no orientation packet")
        assertThat(read("get_orientation_packet", "task_id" to unlinked.id)).contains("not from a repository linked")
        verify(exactly = 0) { orientationService.getForAuthoring(unlinked.id, any()) }
    }
}
