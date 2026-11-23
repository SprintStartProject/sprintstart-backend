package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.OnboardingGenerationIssueResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

internal class PathOverviewTextTest {
    private fun step(status: StepStatus) = GetOnboardingStepsResponse(
        id = UUID.randomUUID(),
        phaseId = UUID.randomUUID(),
        position = 0,
        title = "Set up the dev box",
        description = "",
        type = StepType.TASK,
        estimatedMinutes = 20,
        isAiAssisted = false,
        status = status,
        completedAt = null,
        skip = null,
        locked = false,
    )

    /** A phase with one step: still open unless [done], which makes it one that is behind them. */
    private fun phase(
        title: String,
        done: Boolean = false,
        locked: Boolean = false,
        description: String = "",
    ) = GetOnboardingPhaseForUserResponse(
        id = UUID.randomUUID(),
        pathId = UUID.randomUUID(),
        position = 0,
        title = title,
        description = description,
        locked = locked,
        steps = listOf(step(if (done) StepStatus.FINISHED else StepStatus.WAITING)),
        questions = emptyList(),
    )

    private fun pathWith(vararg issues: OnboardingGenerationIssueResponse) = GetOnboardingPathForUserResponse(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        createdAt = Instant.EPOCH,
        phases = emptyList(),
        generationIssues = issues.toList(),
    )

    private fun issue(title: String, description: String = "") =
        OnboardingGenerationIssueResponse(UUID.randomUUID(), title, GenerationStatus.EMPTY, description)

    private fun text(block: StringBuilder.() -> Unit) = buildString(block)

    @Test
    fun `a phase reference is its number, its title and its link`() {
        val first = phase("Arrive")
        val second = phase("Ship")

        assertThat(phaseRef(second, listOf(first, second)))
            .isEqualTo("phase 2 “Ship” [link: /onboarding?phase=${second.id}]")
    }

    @Test
    fun `standing counts the phases behind them in words`() {
        val current = phase("Ship")

        assertThat(standing(listOf(current), current)).contains("Nothing is behind them yet.")
        assertThat(standing(listOf(phase("Arrive", done = true), current), current))
            .contains("1 of them is behind them.")
        assertThat(standing(listOf(phase("Arrive", done = true), phase("Learn", done = true), current), current))
            .contains("2 of them are behind them.")
    }

    @Test
    fun `standing names where they are and how many phases there are`() {
        val current = phase("Ship")
        val text = standing(listOf(phase("Arrive", done = true), current), current)

        assertThat(text).startsWith("The hire's onboarding path has 2 phases. They are standing in phase 2, “Ship”")
    }

    @Test
    fun `standing lists the other open, unlocked phases as open alongside`() {
        val current = phase("Ship")
        val alongside = phase("Learn")
        val phases = listOf(phase("Arrive", done = true), current, alongside, phase("Later", locked = true))

        val text = standing(phases, current)

        assertThat(text).contains("these are open alongside it")
        assertThat(text).contains(phaseRef(alongside, phases))
        assertThat(text).doesNotContain("Arrive").doesNotContain("Later")
    }

    @Test
    fun `standing says nothing about other phases when none is open beside it`() {
        val current = phase("Ship")

        assertThat(standing(listOf(phase("Arrive", done = true), current, phase("Later", locked = true)), current))
            .doesNotContain("open alongside")
    }

    @Test
    fun `a choice lists every option with its id, what it is for and where it starts`() {
        val learn = phase("Learn", description = "How the services fit together")
        val ship = phase("Ship")
        val phases = listOf(phase("Arrive", done = true), learn, ship)

        val text = text { appendChoice(listOf(learn, ship), phases) }

        assertThat(text).contains("1 of them is behind them")
        assertThat(text).contains("- ${phaseRef(learn, phases)} [phase_id: ${learn.id}]")
        assertThat(text).contains("    · what it is for: How the services fit together")
        assertThat(text).contains("    · where it starts: the step “Set up the dev box”")
        assertThat(text.lines().filter { it.contains("what it is for") }).hasSize(1)
    }

    @Test
    fun `a choice uses the plural when more than one phase is behind them`() {
        val learn = phase("Learn")
        val phases = listOf(phase("Arrive", done = true), phase("Read", done = true), learn)

        assertThat(text { appendChoice(listOf(learn), phases) }).contains("2 of them are behind them")
    }

    @Test
    fun `a choice names the phases still locked, and only those`() {
        val learn = phase("Learn")
        val locked = phase("Later", locked = true)
        val lockedAndDone = phase("Old", done = true, locked = true)

        val text = text { appendChoice(listOf(learn), listOf(learn, locked, lockedAndDone)) }

        assertThat(text).contains("Still locked: “Later”")
        assertThat(text).doesNotContain("“Old”")
        assertThat(text { appendChoice(listOf(learn), listOf(learn)) }).doesNotContain("Still locked")
    }

    @Test
    fun `ahead lists every other open phase, lower numbers included, and says which are locked`() {
        val current = phase("Ship")
        val phases = listOf(phase("Learn"), phase("Arrive", done = true), current, phase("Later", locked = true))

        val text = text { appendAhead(phases, current) }

        assertThat(text).contains("- 1. “Learn” (open)")
        assertThat(text).contains("- 4. “Later” (locked until its blockers are done)")
        assertThat(text).doesNotContain("Arrive").doesNotContain("“Ship”")
    }

    @Test
    fun `ahead is empty when nothing else is left`() {
        val current = phase("Ship")

        assertThat(text { appendAhead(listOf(phase("Arrive", done = true), current), current) }).isEmpty()
    }

    @Test
    fun `ahead names twelve phases and counts the rest`() {
        val current = phase("Ship")
        val others = (1..14).map { phase("Phase $it") }

        val text = text { appendAhead(listOf(current) + others, current) }

        assertThat(text.lines().count { it.matches(Regex("""- \d+\. .*""")) }).isEqualTo(12)
        assertThat(text).contains("- and 2 more")
        assertThat(text { appendAhead(listOf(current) + others.take(12), current) }).doesNotContain("more")
    }

    @Test
    fun `empty phases are named with their status, id and what they were meant to cover`() {
        val withDescription = issue("Security basics", "How secrets are handled")
        val bare = issue("Release flow")

        val text = text { appendEmptyPhases(pathWith(withDescription, bare)) }

        assertThat(
            text,
        ).contains("- “Security basics” (EMPTY) [phase_id: ${withDescription.phaseId}]: How secrets are handled")
        assertThat(text).contains("- “Release flow” (EMPTY) [phase_id: ${bare.phaseId}]\n")
        assertThat(text).contains("offer add_path_step with that phase_id")
    }

    @Test
    fun `there is nothing to say about empty phases when there are none`() {
        assertThat(text { appendEmptyPhases(pathWith()) }).isEmpty()
    }

    @Test
    fun `at most twelve empty phases are named`() {
        val text = text { appendEmptyPhases(pathWith(*Array(14) { issue("Empty $it") })) }

        assertThat(text.lines().count { it.startsWith("- “Empty") }).isEqualTo(12)
    }
}
