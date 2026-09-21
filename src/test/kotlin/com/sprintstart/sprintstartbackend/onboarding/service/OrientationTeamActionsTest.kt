package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationOrigin
import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationStep
import com.sprintstart.sprintstartbackend.onboarding.model.request.orientation.AuthorOrientationRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.orientation.MyOrientationResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.orientation.OrientationPacketResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class OrientationTeamActionsTest {
    private val f = ContentFixture()
    private val service: TaskOrientationService = mockk(relaxed = true)

    private val assembledAt: Instant = Instant.parse("2026-09-01T10:00:00Z")

    private fun packetFor(taskId: UUID, origin: OrientationOrigin, at: Instant = assembledAt) =
        OrientationPacketResponse(
            taskId = taskId,
            taskTitle = "Fix the typo",
            summary = null,
            sections = emptyList(),
            sources = emptyList(),
            assembledAt = at,
            origin = origin,
        )

    /** What the service says the task has now: nothing, or a packet written by [origin]. */
    private fun current(taskId: UUID, origin: OrientationOrigin?, at: Instant = assembledAt) {
        every { service.getForAuthoring(taskId, f.projectId) } returns
            MyOrientationResponse(
                taskId = taskId,
                taskTitle = "Fix the typo",
                taskUrl = null,
                packet = origin?.let { packetFor(taskId, it, at) },
                reason = null,
            )
    }

    private fun section(
        step: String = "SET_UP",
        title: String = "Install",
        body: String = "Run make setup.",
        citations: JsonArray = JsonArray(emptyList()),
    ) = f.json("step" to step, "title" to title, "body" to body, "citations" to citations)

    @Nested
    inner class Author {
        private val action = AuthorOrientationPacketAction(f.scope, service)

        private fun author(taskId: UUID, vararg sections: JsonObject, summary: String? = null) =
            f.call(
                "author_orientation_packet",
                "task_id" to taskId,
                "summary" to summary,
                "sections" to JsonArray(sections.toList()),
            )

        @Test
        fun `is a standard change`() {
            assertThat(action.risk).isEqualTo(BuddyProposalRisk.STANDARD)
            assertThat(action.area).isEqualTo(TeamArea.CONTENT)
        }

        @Test
        fun `shows the full text a hire will read`() {
            val task = f.task()
            current(task.id, null)

            val draft = f.proposed(
                action.draft(
                    author(
                        task.id,
                        section(body = "Run make setup, then open the dev container."),
                        summary = "Start here",
                    ),
                    f.context,
                ),
            )

            assertThat(draft.preview).contains(
                "orientation packet for “Fix the typo”",
                "Summary: Start here",
                "set up — Install",
                "Run make setup, then open the dev container.",
                "There is no packet for this task yet",
                "AI does not regenerate it",
            )
        }

        @Test
        fun `says what it replaces, and that a person's text is among it`() {
            val task = f.task()
            current(task.id, OrientationOrigin.HUMAN)

            val draft = f.proposed(action.draft(author(task.id, section()), f.context))

            assertThat(draft.preview).contains("replaces the packet that is there now, written by a person")
        }

        @Test
        fun `an assembled packet is named as such`() {
            val task = f.task()
            current(task.id, OrientationOrigin.AI)

            val draft = f.proposed(action.draft(author(task.id, section()), f.context))

            assertThat(draft.preview).contains("assembled by the AI")
        }

        @Test
        fun `a task whose repository is not linked to this project is refused`() {
            val task = f.task(linked = false)

            assertThat(
                f.refusal(action.draft(author(task.id, section()), f.context)),
            ).contains("not from a repository linked")
        }

        @Test
        fun `a task that does not exist is refused the same way`() {
            val reason = f.refusal(action.draft(author(UUID.randomUUID(), section()), f.context))

            assertThat(reason).contains("not from a repository linked")
        }

        @Test
        fun `sections the service would refuse are refused at proposal`() {
            val task = f.task()
            current(task.id, null)

            assertThat(f.refusal(action.draft(author(task.id), f.context))).contains("at least one section")
            assertThat(f.refusal(action.draft(author(task.id, section(title = " ")), f.context)))
                .contains("both a title and a body")
            assertThat(f.refusal(action.draft(author(task.id, section(step = "DEPLOY")), f.context)))
                .contains("needs a step", "SET_UP", "OPEN_THE_PR")
        }

        @Test
        fun `a citation link that is not a web address is refused`() {
            val task = f.task()
            current(task.id, null)
            val bad = section(citations = JsonArray(listOf(f.json("filename" to "x", "source_url" to "javascript:x"))))

            assertThat(f.refusal(action.draft(author(task.id, bad), f.context))).contains("not a web address")
        }

        @Test
        fun `a confirm is turned down when the packet was replaced since the preview`() {
            val task = f.task()
            current(task.id, OrientationOrigin.AI, at = assembledAt)
            val proposed = f.proposed(action.draft(author(task.id, section()), f.context))

            current(task.id, OrientationOrigin.HUMAN, at = assembledAt.plusSeconds(60))

            assertThat(action.recheck(proposed.params, f.context)).contains("changed since this was proposed")
        }

        @Test
        fun `a confirm with the packet as it was passes, and one for a task that left the project does not`() {
            val task = f.task()
            current(task.id, OrientationOrigin.AI)
            val proposed = f.proposed(action.draft(author(task.id, section()), f.context))

            assertThat(action.recheck(proposed.params, f.context)).isNull()

            f.task(linked = false).let { unlinked ->
                assertThat(
                    action.recheck(f.json("task_id" to unlinked.id), f.context),
                ).contains("not from a repository linked")
            }
        }

        @Test
        fun `nothing is saved while drafting`() {
            val task = f.task()
            current(task.id, null)

            action.draft(author(task.id, section()), f.context)

            verify(exactly = 0) { service.authorPacket(any(), any(), any()) }
        }

        @Test
        fun `performing saves the stored text for this project`() =
            runTest {
                val task = f.task()
                current(task.id, null)
                val proposed = f.proposed(
                    action.draft(
                        author(
                            task.id,
                            section(step = "check_locally", title = "Test", body = "make test"),
                            summary = "Hi",
                        ),
                        f.context,
                    ),
                )
                val request = slot<AuthorOrientationRequest>()
                every { service.authorPacket(task.id, f.projectId, capture(request)) } returns mockk(relaxed = true)

                action.perform(proposed.params, f.context)

                assertThat(request.captured.summary).isEqualTo("Hi")
                assertThat(
                    request.captured.sections
                        .single()
                        .step,
                ).isEqualTo(OrientationStep.CHECK_LOCALLY)
                assertThat(
                    request.captured.sections
                        .single()
                        .body,
                ).isEqualTo("make test")
            }
    }

    @Nested
    inner class Revert {
        private val action = RevertOrientationPacketAction(f.scope, service)

        @Test
        fun `is destructive`() {
            assertThat(action.risk).isEqualTo(BuddyProposalRisk.DESTRUCTIVE)
        }

        @Test
        fun `dropping a person's packet says their text is deleted`() {
            val task = f.task()
            current(task.id, OrientationOrigin.HUMAN)

            val draft = f.proposed(action.draft(f.call("revert_orientation_packet", "task_id" to task.id), f.context))

            assertThat(draft.preview).contains("written by a person", "their text is deleted", "cannot be undone")
        }

        @Test
        fun `dropping an assembled packet makes no such claim`() {
            val task = f.task()
            current(task.id, OrientationOrigin.AI)

            val draft = f.proposed(action.draft(f.call("revert_orientation_packet", "task_id" to task.id), f.context))

            assertThat(draft.preview).doesNotContain("their text is deleted").contains("assembled from the docs again")
        }

        @Test
        fun `a task with no packet is refused`() {
            val task = f.task()
            current(task.id, null)

            assertThat(f.refusal(action.draft(f.call("revert_orientation_packet", "task_id" to task.id), f.context)))
                .contains("no packet")
        }

        @Test
        fun `a task whose repository is not linked to this project is refused`() {
            val task = f.task(linked = false)

            assertThat(f.refusal(action.draft(f.call("revert_orientation_packet", "task_id" to task.id), f.context)))
                .contains("not from a repository linked")
        }

        @Test
        fun `a confirm is turned down when the packet was replaced since`() {
            val task = f.task()
            current(task.id, OrientationOrigin.HUMAN)
            val proposed = f.proposed(
                action.draft(f.call("revert_orientation_packet", "task_id" to task.id), f.context),
            )

            current(task.id, OrientationOrigin.HUMAN, at = assembledAt.plusSeconds(1))

            assertThat(action.recheck(proposed.params, f.context)).contains("changed since")
        }

        @Test
        fun `performing drops the packet of this project`() =
            runTest {
                val task = f.task()

                action.perform(f.json("task_id" to task.id), f.context)

                verify { service.revertToAi(task.id, f.projectId) }
            }
    }
}
