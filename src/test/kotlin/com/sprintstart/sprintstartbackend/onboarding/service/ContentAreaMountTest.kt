package com.sprintstart.sprintstartbackend.onboarding.service

import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Clock

/**
 * What opening the content area hands the model, checked through the same mounting the buddy uses.
 *
 * The acceptance criteria that are about the set of tools rather than about one of them live here:
 * exactly these tools, and none that can set a hire's progress.
 */
class ContentAreaMountTest {
    private val f = ContentFixture()

    private val handlers: List<TeamActionHandler> = listOf(
        AddPhaseAction(f.scope, f.pathElements, mockk()),
        UpdatePhaseAction(f.scope, mockk()),
        DeletePhaseAction(f.scope, mockk()),
        AddStepAction(f.scope, mockk()),
        UpdateStepAction(f.scope, mockk()),
        DeleteStepAction(f.scope, mockk()),
        AddTaskAction(f.scope, mockk()),
        UpdateTaskAction(f.scope, mockk()),
        DeleteTaskAction(f.scope, mockk()),
        AddResourceAction(f.scope, mockk()),
        UpdateResourceAction(f.scope, mockk()),
        DeleteResourceAction(f.scope, mockk()),
        ReplacePhaseChecksAction(f.scope, mockk()),
        AcceptSkipAction(f.scope, mockk()),
        DenySkipAction(f.scope, mockk()),
        DeleteSkipAction(f.scope, mockk()),
        MarkFeedbackReadAction(f.scope, mockk()),
        ResetMemberPathAction(f.scope, f.pathElements, mockk()),
        AuthorOrientationPacketAction(f.scope, mockk()),
        RevertOrientationPacketAction(f.scope, mockk()),
    )

    private val reads = ContentTeamTools(
        f.scope,
        f.pathElements,
        mockk(),
        mockk(),
        mockk(),
        mockk(),
        mockk(),
        mockk(),
    )

    private val proposals: BuddyProposalService = run {
        val provider: ObjectProvider<TeamActionHandler> = mockk()
        every { provider.orderedStream() } answers { handlers.stream() }
        BuddyProposalService(mockk(), mockk(), provider, Clock.systemUTC())
    }

    private fun propertyNames(schema: JsonObject): Set<String> =
        (schema["properties"] as? JsonObject)?.keys.orEmpty()

    @Test
    fun `opening the content area mounts exactly its reads and actions`() {
        val mounted = proposals.actionSpecs(setOf(TeamArea.CONTENT)).map { it.name } + reads.toolSpecs().map { it.name }

        assertThat(mounted).containsExactlyInAnyOrder(
            "get_member_path",
            "list_pending_skips",
            "list_feedback",
            "get_phase_checks",
            "get_orientation_packet",
            "add_phase",
            "update_phase",
            "delete_phase",
            "add_step",
            "update_step",
            "delete_step",
            "add_task",
            "update_task",
            "delete_task",
            "add_resource",
            "update_resource",
            "delete_resource",
            "replace_phase_checks",
            "accept_skip",
            "deny_skip",
            "delete_skip",
            "mark_feedback_read",
            "reset_member_path",
            "author_orientation_packet",
            "revert_orientation_packet",
        )
    }

    @Test
    fun `no two tools share a name, because handlers are keyed by it`() {
        val names = handlers.map { it.spec.name } + reads.toolSpecs().map { it.name }

        assertThat(names).doesNotHaveDuplicates()
    }

    @Test
    fun `every action belongs to the content area and its risk is declared, not taken from the model`() {
        assertThat(handlers.map { it.area }).containsOnly(TeamArea.CONTENT)
        handlers.forEach { assertThat(propertyNames(it.spec.parameters)).doesNotContain("risk") }
    }

    @Test
    fun `nothing in the area can tick a task or start, finish or complete a step`() {
        val names = handlers.map { it.spec.name }
        assertThat(names.filter { Regex("start|finish|complete|tick|done").containsMatchIn(it) }).isEmpty()

        handlers.forEach { handler ->
            assertThat(propertyNames(handler.spec.parameters))
                .describedAs(handler.spec.name)
                .doesNotContain("finished", "status", "completed", "started", "done")
        }
    }

    @Test
    fun `an unopened area's tools are not mounted`() {
        assertThat(proposals.actionSpecs(setOf(TeamArea.TEAM))).isEmpty()
        assertThat(proposals.actionAreas()).containsExactly(TeamArea.CONTENT)
    }
}
