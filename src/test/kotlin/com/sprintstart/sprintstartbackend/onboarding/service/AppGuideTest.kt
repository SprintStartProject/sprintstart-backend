package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AppGuideTest {
    /**
     * Pinned on purpose. The guide is a hand-written copy of the frontend's routes, so adding,
     * renaming or removing a page has to be a deliberate edit here — check the frontend's
     * `AppRouter.tsx` and `accessPolicy.ts` when this fails, not just the expected list.
     */
    @Test
    fun `describes exactly these routes`() {
        assertThat(AppGuide.pages.map { it.path }).containsExactly(
            "/",
            "/board",
            "/buddy",
            "/knowledge-base",
            "/onboarding",
            "/settings",
            "/pm-dashboard",
            "/team-management",
            "/team/{member_id}",
            "/insights/onboarding",
            "/insights/faq",
            "/insights/knowledge-gaps",
            "/insights/knowledge-requests",
            "/data-ingestion",
            "/blueprints",
            "/hire-setup",
            "/admin",
        )
    }

    /** Anything else would render as a new tab to somebody else's site, or not as a link at all. */
    @Test
    fun `every path is root-relative`() {
        AppGuide.pages.forEach { page ->
            assertThat(page.path).startsWith("/").doesNotStartWith("//")
        }
    }

    /**
     * A how-to's deep link must land on its own page — a link to another page's tab would send
     * somebody to a page the guide never checked they can open.
     */
    @Test
    fun `every deep link stays on its own page`() {
        AppGuide.pages.forEach { page ->
            page.howTos.mapNotNull { it.link }.forEach { link ->
                assertThat(link.substringBefore('?')).isEqualTo(page.path)
            }
        }
    }

    /** The frontend only narrows PM access on pages that PM, HR and admins can all open. */
    @Test
    fun `a manager-scoped page is open to PM, HR and admins`() {
        AppGuide.pages.filter { it.managerScoped }.forEach { page ->
            assertThat(page.roles).containsExactlyInAnyOrder(Role.PM, Role.HR, Role.ADMIN)
        }
    }

    @Test
    fun `names the page behind a detail route`() {
        assertThat(AppGuide.pageAt("/team/1234")?.name).isEqualTo("Team member")
        assertThat(AppGuide.pageAt("/blueprints/abc")?.name).isEqualTo("Blueprints")
    }

    /** `/team-management` starts with `/team`, but not with the member page's `/team/`. */
    @Test
    fun `does not mistake team management for a member page`() {
        assertThat(AppGuide.pageAt("/team-management")?.name).isEqualTo("Team")
    }

    @Test
    fun `knows nothing about a route it does not describe`() {
        assertThat(AppGuide.pageAt("/nowhere")).isNull()
    }
}
