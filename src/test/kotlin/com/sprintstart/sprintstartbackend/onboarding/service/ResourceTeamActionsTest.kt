package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.CreateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.UpdateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourceResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class ResourceTeamActionsTest {
    private val f = ContentFixture()
    private val resourceService: OnboardingResourceService = mockk(relaxed = true)

    @Nested
    inner class Add {
        private val action = AddResourceAction(f.scope, resourceService)
        private val stepId = UUID.randomUUID()

        private fun call(url: String) = f.call("add_resource", "step_id" to stepId, "title" to "Docs", "url" to url)

        @Test
        fun `shows the address the hire will be sent to`() {
            f.element(PathElementKind.STEP, stepId, title = "Install")

            val draft = f.proposed(action.draft(call("https://docs.example.com/start"), f.context))

            assertThat(draft.preview).contains("“Install”", "“Docs” — https://docs.example.com/start")
        }

        @Test
        fun `only web links are accepted, because a hire clicks them`() {
            f.element(PathElementKind.STEP, stepId)

            listOf("javascript:alert(1)", "file:///etc/passwd", "docs.example.com", "https://", "not a url").forEach {
                assertThat(f.refusal(action.draft(call(it), f.context))).describedAs(it).contains("http")
            }
            assertThat(action.draft(call("http://wiki.internal/setup"), f.context))
                .isInstanceOf(TeamActionDraft.Proposed::class.java)
        }

        @Test
        fun `a step on another person's path is refused`() {
            f.element(PathElementKind.STEP, stepId, owner = f.outsiderId)

            assertThat(f.refusal(action.draft(call("https://x.io"), f.context))).contains("not on the onboarding path")
        }

        @Test
        fun `performing creates the resource with what was stored`() =
            runTest {
                val request = slot<CreateOnboardingResourceRequest>()
                every { resourceService.createOnboardingResourceForStepId(stepId, capture(request)) } returns
                    mockk(relaxed = true)

                action.perform(
                    f.json("step_id" to stepId, "title" to "Docs", "url" to "https://x.io", "description" to "d"),
                    f.context,
                )

                assertThat(request.captured).isEqualTo(CreateOnboardingResourceRequest("Docs", "d", "https://x.io"))
            }
    }

    @Nested
    inner class Update {
        private val action = UpdateResourceAction(f.scope, resourceService)
        private val resourceId = UUID.randomUUID()

        private fun existing() {
            f.element(PathElementKind.RESOURCE, resourceId)
            every { resourceService.getOnboardingResourceById(resourceId) } returns
                GetOnboardingResourceResponse(resourceId, UUID.randomUUID(), "Docs", "old", "https://old.io")
        }

        @Test
        fun `previews the address change`() {
            existing()

            val draft = f.proposed(
                action.draft(
                    f.call("update_resource", "resource_id" to resourceId, "url" to "https://new.io"),
                    f.context,
                ),
            )

            assertThat(draft.preview).contains("https://old.io becomes https://new.io")
        }

        @Test
        fun `a new address that is not a web link is refused`() {
            existing()

            val reason = f.refusal(
                action.draft(
                    f.call("update_resource", "resource_id" to resourceId, "url" to "javascript:x"),
                    f.context,
                ),
            )

            assertThat(reason).contains("http")
        }

        @Test
        fun `repeating what is there is refused as no change`() {
            existing()

            val reason = f.refusal(
                action.draft(f.call("update_resource", "resource_id" to resourceId, "title" to "Docs"), f.context),
            )

            assertThat(reason).contains("Nothing would change")
        }

        @Test
        fun `performing keeps what was not stored`() =
            runTest {
                existing()
                val request = slot<UpdateOnboardingResourceRequest>()
                every { resourceService.updateOnboardingResourceById(resourceId, capture(request)) } returns
                    mockk(relaxed = true)

                action.perform(f.json("resource_id" to resourceId, "url" to "https://new.io"), f.context)

                assertThat(request.captured).isEqualTo(UpdateOnboardingResourceRequest("Docs", "old", "https://new.io"))
            }
    }

    @Nested
    inner class Delete {
        private val action = DeleteResourceAction(f.scope, resourceService)
        private val resourceId = UUID.randomUUID()

        @Test
        fun `names the link and refuses one on another person's path`() {
            f.element(PathElementKind.RESOURCE, resourceId, title = "Docs")
            assertThat(
                f.proposed(action.draft(f.call("delete_resource", "resource_id" to resourceId), f.context)).preview,
            ).contains("“Docs”")

            f.element(PathElementKind.RESOURCE, resourceId, owner = f.outsiderId)
            assertThat(f.refusal(action.draft(f.call("delete_resource", "resource_id" to resourceId), f.context)))
                .contains("not on the onboarding path")
        }

        @Test
        fun `performing deletes the resource by id`() =
            runTest {
                action.perform(f.json("resource_id" to resourceId), f.context)

                verify { resourceService.deleteOnboardingResourceById(resourceId) }
            }
    }
}
