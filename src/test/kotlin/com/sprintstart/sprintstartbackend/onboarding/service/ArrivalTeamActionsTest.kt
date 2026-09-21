package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class ArrivalTeamActionsTest {
    private val arrivalStepService: ArrivalStepService = mockk(relaxed = true)

    private val projectId = UUID.randomUUID()
    private val context = TeamToolContext(userId = UUID.randomUUID(), authId = "auth|pm", projectId = projectId)

    private fun step(
        key: String,
        title: String = "A step",
        description: String? = null,
        href: String? = null,
        position: Int = 0,
        settledBy: Rigor = Rigor.DECLARED,
    ) = ArrivalStep(
        key = key,
        projectId = projectId,
        title = title,
        description = description,
        href = href,
        position = position,
        settledBy = settledBy,
    )

    private fun call(name: String, arguments: JsonObject) =
        BuddyToolCallDto(id = "c1", name = name, arguments = arguments)

    private fun onList(vararg steps: ArrivalStep) {
        every { arrivalStepService.listForAuthoring(projectId) } returns steps.toList()
    }

    private fun proposed(draft: TeamActionDraft): TeamActionDraft.Proposed {
        assertThat(draft).isInstanceOf(TeamActionDraft.Proposed::class.java)
        return draft as TeamActionDraft.Proposed
    }

    private fun refusal(draft: TeamActionDraft): String {
        assertThat(draft).isInstanceOf(TeamActionDraft.Refused::class.java)
        return (draft as TeamActionDraft.Refused).reason
    }

    @Nested
    inner class Create {
        private val action = CreateArrivalStepsAction(arrivalStepService)

        private fun steps(vararg entries: Map<String, String>) = buildJsonObject {
            putJsonArray("steps") {
                entries.forEach { entry -> addJsonObject { entry.forEach { (k, v) -> put(k, v) } } }
            }
        }

        @Test
        fun `is a standard change in the arrival area`() {
            assertThat(action.area).isEqualTo(TeamArea.ARRIVAL)
            assertThat(action.risk).isEqualTo(BuddyProposalRisk.STANDARD)
            assertThat(action.spec.name).isEqualTo("create_arrival_steps")
        }

        @Test
        fun `several steps are one proposal with every step in the preview`() {
            onList()

            val draft = proposed(
                action.draft(
                    call(
                        "create_arrival_steps",
                        steps(
                            mapOf("key" to "laptop", "title" to "Collect your laptop"),
                            mapOf("key" to "vpn", "title" to "Get on the VPN"),
                        ),
                    ),
                    context,
                ),
            )

            assertThat(draft.label).isEqualTo("Add 2 arrival steps")
            assertThat(draft.preview).contains("Collect your laptop [key: laptop]")
            assertThat(draft.preview).contains("Get on the VPN [key: vpn]")
        }

        @Test
        fun `a derivable key is previewed as observed whatever was asked for`() {
            onList()

            val draft = proposed(
                action.draft(
                    call(
                        "create_arrival_steps",
                        steps(
                            mapOf(
                                "key" to "github-account",
                                "title" to "Add your GitHub username",
                                "settled_by" to "declared",
                            ),
                        ),
                    ),
                    context,
                ),
            )

            assertThat(draft.preview).contains("Settled by the system observing it")
            assertThat(draft.preview).contains("the system checks this key itself")
            assertThat(draft.preview).doesNotContain("the hire declaring it")
        }

        @Test
        fun `a derivable key is stored as observed, so the confirm runs what was previewed`() = runTest {
            val captured = slot<List<NewArrivalStep>>()
            every { arrivalStepService.createAll(projectId, capture(captured)) } returns emptyList()

            action.perform(
                buildJsonObject {
                    putJsonArray("steps") {
                        addJsonObject {
                            put("key", "github-account")
                            put("title", "Add your GitHub username")
                            put("settled_by", Rigor.OBSERVED.name)
                        }
                    }
                },
                context,
            )

            assertThat(captured.captured.single().settledBy).isEqualTo(Rigor.OBSERVED)
        }

        @Test
        fun `a key the company list already uses says so, because the project's one replaces it`() {
            onList()
            every { arrivalStepService.listForAuthoring(null) } returns
                listOf(
                    ArrivalStep(key = "vpn", projectId = null, title = "Company VPN"),
                )

            val draft = proposed(
                action.draft(
                    call("create_arrival_steps", steps(mapOf("key" to "vpn", "title" to "Team VPN"))),
                    context,
                ),
            )

            // Allowed -- a project may override a company step -- but the manager confirms the
            // override, not just the addition.
            assertThat(draft.preview).contains("takes the place of the company-wide step “Company VPN”")
            assertThat(draft.preview).contains("everyone on this project")
        }

        @Test
        fun `a key nothing else uses says nothing about replacing`() {
            onList()
            every { arrivalStepService.listForAuthoring(null) } returns
                listOf(ArrivalStep(key = "badge", projectId = null, title = "Company badge"))

            val draft = proposed(
                action.draft(
                    call("create_arrival_steps", steps(mapOf("key" to "vpn", "title" to "Team VPN"))),
                    context,
                ),
            )

            assertThat(draft.preview).doesNotContain("takes the place of")
        }

        @Test
        fun `a key the project already has fails the whole batch with a plain message`() {
            onList(step("laptop"))

            val reason = refusal(
                action.draft(
                    call(
                        "create_arrival_steps",
                        steps(
                            mapOf("key" to "vpn", "title" to "Get on the VPN"),
                            mapOf("key" to "laptop", "title" to "Collect your laptop"),
                        ),
                    ),
                    context,
                ),
            )

            assertThat(reason).contains("already has an arrival step 'laptop'")
            verify(exactly = 0) { arrivalStepService.createAll(any(), any()) }
        }

        @Test
        fun `the same key twice in one batch is refused`() {
            onList()

            val reason = refusal(
                action.draft(
                    call(
                        "create_arrival_steps",
                        steps(
                            mapOf("key" to "vpn", "title" to "One"),
                            mapOf("key" to "vpn", "title" to "Two"),
                        ),
                    ),
                    context,
                ),
            )

            assertThat(reason).contains("appears twice")
        }

        @Test
        fun `a malformed key is a sentence, not a failed request`() {
            onList()

            val reason = refusal(
                action.draft(
                    call("create_arrival_steps", steps(mapOf("key" to "Not A Key!", "title" to "x"))),
                    context,
                ),
            )

            assertThat(reason).contains("not a usable key")
        }

        @Test
        fun `a step without a title is refused`() {
            onList()

            val reason = refusal(
                action.draft(call("create_arrival_steps", steps(mapOf("key" to "vpn", "title" to " "))), context),
            )

            assertThat(reason).contains("no title")
        }

        @Test
        fun `an empty batch is refused`() {
            onList()

            val reason = refusal(action.draft(call("create_arrival_steps", steps()), context))

            assertThat(reason).contains("nothing to offer")
        }

        @Test
        fun `a key taken since the preview stops the confirm`() {
            onList(step("vpn"))

            val stale = action.recheck(
                buildJsonObject { putJsonArray("steps") { addJsonObject { put("key", "vpn") } } },
                context,
            )

            assertThat(stale).contains("was added since")
        }

        @Test
        fun `the confirm writes to the turn's project, never the default list`() = runTest {
            every { arrivalStepService.createAll(projectId, any()) } returns emptyList()

            action.perform(
                buildJsonObject {
                    putJsonArray("steps") {
                        addJsonObject {
                            put("key", "vpn")
                            put("title", "Get on the VPN")
                        }
                    }
                },
                context,
            )

            verify { arrivalStepService.createAll(projectId, any()) }
            verify(exactly = 0) { arrivalStepService.createAll(null, any()) }
        }
    }

    @Nested
    inner class Update {
        private val action = UpdateArrivalStepAction(arrivalStepService)

        private fun args(vararg pairs: Pair<String, String>) = buildJsonObject {
            pairs.forEach { (k, v) -> put(k, v) }
        }

        @Test
        fun `offers no way to change a key`() {
            assertThat(action.spec.parameters.toString()).doesNotContain("new_key")
            assertThat(action.spec.description).contains("key cannot be changed")
        }

        @Test
        fun `shows what each field changes from and to`() {
            onList(step("vpn", title = "Get on the VPN", description = "Old"))

            val draft = proposed(
                action.draft(
                    call("update_arrival_step", args("key" to "vpn", "title" to "Join the VPN")),
                    context,
                ),
            )

            assertThat(draft.preview).contains("“Get on the VPN” becomes “Join the VPN”")
            assertThat(draft.preview).contains("The key stays vpn")
        }

        @Test
        fun `a step on another project is not reachable by key`() {
            onList(step("vpn"))

            val reason = refusal(
                action.draft(call("update_arrival_step", args("key" to "somebody-elses")), context),
            )

            assertThat(reason).contains("no arrival step with that key")
        }

        @Test
        fun `changing nothing is refused`() {
            onList(step("vpn"))

            val reason = refusal(action.draft(call("update_arrival_step", args("key" to "vpn")), context))

            assertThat(reason).contains("Nothing was given to change")
        }

        @Test
        fun `settled_by on a derived key is refused rather than silently ignored`() {
            onList(step("github-account", settledBy = Rigor.OBSERVED))

            val reason = refusal(
                action.draft(
                    call("update_arrival_step", args("key" to "github-account", "settled_by" to "declared")),
                    context,
                ),
            )

            assertThat(reason).contains("system checks itself")
        }

        @Test
        fun `a place another step already holds points at reordering instead`() {
            onList(step("vpn", position = 0), step("laptop", title = "Laptop", position = 1))

            val reason = refusal(
                action.draft(call("update_arrival_step", args("key" to "vpn", "position" to "1")), context),
            )

            assertThat(reason).contains("already at place 1")
            assertThat(reason).contains("reorder_arrival_steps")
        }

        @Test
        fun `a place past the end of the list is refused`() {
            onList(step("vpn", position = 0), step("laptop", title = "Laptop", position = 1))

            val reason = refusal(
                action.draft(call("update_arrival_step", args("key" to "vpn", "position" to "9")), context),
            )

            assertThat(reason).contains("places run from 0 to 1")
            assertThat(reason).contains("reorder_arrival_steps")
        }

        @Test
        fun `taking the description off is previewed as a removal, not as an empty value`() {
            onList(step("vpn", title = "Get on the VPN", description = "Ask IT"))

            val draft = proposed(
                action.draft(
                    call(
                        "update_arrival_step",
                        buildJsonObject {
                            put("key", "vpn")
                            put("clear_description", true)
                        },
                    ),
                    context,
                ),
            )

            assertThat(draft.preview).contains("“Ask IT” is taken off, leaving none")
        }

        @Test
        fun `a new description and clear_description together are refused rather than one winning`() {
            onList(step("vpn", description = "Ask IT"))

            val reason = refusal(
                action.draft(
                    call(
                        "update_arrival_step",
                        buildJsonObject {
                            put("key", "vpn")
                            put("description", "Ask the platform team")
                            put("clear_description", true)
                        },
                    ),
                    context,
                ),
            )

            assertThat(reason).contains("not both")
        }

        @Test
        fun `taking off a link the step does not have is refused`() {
            onList(step("vpn", href = null))

            val reason = refusal(
                action.draft(
                    call(
                        "update_arrival_step",
                        buildJsonObject {
                            put("key", "vpn")
                            put("clear_href", true)
                        },
                    ),
                    context,
                ),
            )

            assertThat(reason).contains("no href to take off")
        }

        @Test
        fun `a non-numeric place is refused`() {
            onList(step("vpn"))

            val reason = refusal(
                action.draft(call("update_arrival_step", args("key" to "vpn", "position" to "first")), context),
            )

            assertThat(reason).contains("not a place in the list")
        }

        @Test
        fun `a step removed since the preview stops the confirm`() {
            onList()

            assertThat(action.recheck(buildJsonObject { put("key", "vpn") }, context))
                .contains("changed or removed since")
        }

        @Test
        fun `a place taken since the preview stops the confirm`() {
            onList(step("vpn", position = 0), step("laptop", title = "Laptop", position = 1))

            val params = buildJsonObject {
                put("key", "vpn")
                put("position", "1")
            }

            assertThat(action.recheck(params, context)).contains("“Laptop” took place 1 in the meantime")
        }

        @Test
        fun `a list shorter than the confirmed place stops the confirm`() {
            onList(step("vpn", position = 0))

            val params = buildJsonObject {
                put("key", "vpn")
                put("position", "3")
            }

            assertThat(action.recheck(params, context)).contains("shorter than it was")
        }

        @Test
        fun `a place still free at the confirm is let through`() {
            onList(step("vpn", position = 0), step("laptop", title = "Laptop", position = 1))

            val params = buildJsonObject {
                put("key", "laptop")
                put("position", "1")
            }

            assertThat(action.recheck(params, context)).isNull()
        }

        @Test
        fun `the confirm updates within the turn's project`() = runTest {
            val params = buildJsonObject {
                put("key", "vpn")
                put("title", "Join the VPN")
            }

            action.perform(params, context)

            verify { arrivalStepService.update("vpn", projectId, "Join the VPN", null, null, null, null) }
        }

        @Test
        fun `the confirm takes a description off, rather than reading blank as no change`() = runTest {
            val params = buildJsonObject {
                put("key", "vpn")
                put("description", "")
            }

            action.perform(params, context)

            verify { arrivalStepService.update("vpn", projectId, null, "", null, null, null) }
        }
    }

    @Nested
    inner class Reorder {
        private val action = ReorderArrivalStepsAction(arrivalStepService)

        private fun order(vararg keys: String) = buildJsonObject {
            putJsonArray("ordered_keys") { keys.forEach { add(it) } }
        }

        @Test
        fun `offers the new order as a numbered list`() {
            onList(step("a", title = "First", position = 0), step("b", title = "Second", position = 1))

            val draft = proposed(action.draft(call("reorder_arrival_steps", order("b", "a")), context))

            assertThat(draft.preview).contains("1. Second [key: b]")
            assertThat(draft.preview).contains("2. First [key: a]")
            assertThat(draft.preview).contains("Only the order changes")
        }

        @Test
        fun `a partial order is refused, so no step is left on a colliding place`() {
            onList(step("a", position = 0), step("b", position = 1))

            val reason = refusal(action.draft(call("reorder_arrival_steps", order("b")), context))

            assertThat(reason).contains("left out 'a'")
        }

        @Test
        fun `a key this project does not have is refused`() {
            onList(step("a"))

            val reason = refusal(action.draft(call("reorder_arrival_steps", order("a", "elsewhere")), context))

            assertThat(reason).contains("no arrival step 'elsewhere'")
        }

        @Test
        fun `a repeated key is refused`() {
            onList(step("a"), step("b"))

            val reason = refusal(action.draft(call("reorder_arrival_steps", order("a", "a")), context))

            assertThat(reason).contains("appears 2 times")
        }

        @Test
        fun `the order it is already in is not offered`() {
            onList(step("a", position = 0), step("b", position = 1))

            val reason = refusal(action.draft(call("reorder_arrival_steps", order("a", "b")), context))

            assertThat(reason).contains("already in")
        }

        @Test
        fun `a list that changed since the preview stops the confirm`() {
            onList(step("a"), step("b"), step("c"))

            assertThat(action.recheck(order("a", "b"), context)).contains("changed since")
        }

        @Test
        fun `the confirm reorders within the turn's project`() = runTest {
            action.perform(order("b", "a"), context)

            verify { arrivalStepService.reorder(projectId, listOf("b", "a")) }
        }
    }

    @Nested
    inner class Delete {
        private val action = DeleteArrivalStepAction(arrivalStepService)

        @Test
        fun `is marked destructive`() {
            assertThat(action.risk).isEqualTo(BuddyProposalRisk.DESTRUCTIVE)
        }

        @Test
        fun `says what survives, because hires' records are kept against the key`() {
            onList(step("vpn", title = "Get on the VPN"))

            val draft = proposed(
                action.draft(call("delete_arrival_step", buildJsonObject { put("key", "vpn") }), context),
            )

            assertThat(draft.label).contains("Remove arrival step")
            assertThat(draft.preview).contains("stops appearing for everyone on this project")
            assertThat(draft.preview).contains("adding a step with that key back restores it")
        }

        @Test
        fun `removing an override says the company step comes back, not that the step goes away`() {
            onList(step("vpn", title = "Team VPN"))
            every { arrivalStepService.listForAuthoring(null) } returns
                listOf(ArrivalStep(key = "vpn", projectId = null, title = "Company VPN"))

            val draft = proposed(
                action.draft(call("delete_arrival_step", buildJsonObject { put("key", "vpn") }), context),
            )

            assertThat(draft.preview).contains("company-wide step “Company VPN” takes its place")
            assertThat(draft.preview).contains("it reverts")
            assertThat(draft.preview).doesNotContain("It stops appearing for everyone on this project.")
        }

        @Test
        fun `a step on another project is not reachable by key`() {
            onList()

            val reason = refusal(
                action.draft(call("delete_arrival_step", buildJsonObject { put("key", "vpn") }), context),
            )

            assertThat(reason).contains("no arrival step with that key")
        }

        @Test
        fun `a step removed since the preview stops the confirm`() {
            onList()

            assertThat(action.recheck(buildJsonObject { put("key", "vpn") }, context))
                .contains("changed or removed since")
        }

        @Test
        fun `the confirm deletes within the turn's project`() = runTest {
            action.perform(buildJsonObject { put("key", "vpn") }, context)

            verify { arrivalStepService.delete("vpn", projectId) }
            verify(exactly = 0) { arrivalStepService.delete("vpn", null) }
        }
    }
}
