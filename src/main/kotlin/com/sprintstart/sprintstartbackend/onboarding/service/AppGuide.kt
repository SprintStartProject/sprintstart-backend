package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.user.external.enums.Role

/**
 * One thing a person can do on a page, and who may do it.
 *
 * @property task What it achieves, in the words somebody would ask for it ("create a project").
 * @property steps Where to click, in the UI's own labels.
 * @property doers Who may do it, when that is narrower than who may open the page. A page HR can
 * open can still hold a button only an admin may press, and a guide that ignores the difference
 * sends HR to a button that refuses them.
 * @property link A deep link that lands on the right tab already (`/team-management?tab=roles`),
 * when the page keeps its tab in the URL. Only ever a parameter the frontend actually reads: a
 * link that lands on the wrong tab is worse than a plain one.
 */
data class AppGuideHowTo(
    val task: String,
    val steps: String,
    val doers: Set<Role>? = null,
    val link: String? = null,
)

/**
 * One page of the Sprintstart app, described for the buddy.
 *
 * @property path The route, which is also how the buddy links to the page. Always root-relative,
 * so the chat renders it as an in-app link rather than a new tab. A `{placeholder}` marks an id the
 * buddy has to fill in from another tool before linking.
 * @property matches Prefixes of detail routes that belong to this page (`/team/` for one member),
 * so the page somebody is looking at can be named even when its URL carries an id.
 * @property roles Who may open it — mirrors the frontend's `accessPolicy.ts` `routePermissions`.
 * @property managerScoped Whether a PM additionally has to manage the selected project — mirrors
 * `MANAGER_ASSIGNMENT_ROUTES` there. HR and admins are gated by role alone.
 */
data class AppGuidePage(
    val name: String,
    val path: String,
    val whereToFind: String,
    val purpose: String,
    val howTos: List<AppGuideHowTo> = emptyList(),
    val roles: Set<Role> = EVERYONE,
    val managerScoped: Boolean = false,
    val matches: List<String> = emptyList(),
) {
    /** Whether [path] needs an id filled in before it can be linked (`/team/{member_id}`). */
    val needsId: Boolean get() = '{' in path
}

private val EVERYONE = setOf(Role.USER, Role.PM, Role.HR, Role.ADMIN)
private val MANAGERS = setOf(Role.PM, Role.HR, Role.ADMIN)
private val ADMIN_HR = setOf(Role.HR, Role.ADMIN)
private val PM_ADMIN = setOf(Role.PM, Role.ADMIN)
private val ADMIN_ONLY = setOf(Role.ADMIN)

// The PM area is one page with a tab bar (`PmWorkspace` in the frontend); six of the routes below
// are its tabs. Named once so every entry says where the tab bar is the same way.
private const val PM_TAB = "PM Dashboard (sidebar) → the"

/**
 * The map of the app the buddy reads from, so "where do I…" is answered from the app rather than
 * from the project's documentation — which describes the *project*, and has never heard of a page
 * called PM Dashboard.
 *
 * ### Keeping it true
 *
 * This is a hand-written copy of what the frontend shows, and it goes stale the moment a page is
 * renamed there. What keeps that visible rather than silent:
 *
 * - The labels are the UI's own (sidebar entries, tab names, button text — quoted, so a rename is
 *   a grep away).
 * - `AppGuideTest` pins the route list here, and the frontend's `appGuideRoutes.test.ts` fails
 *   when `accessPolicy.ts` gains a route that neither this guide describes nor is excluded on
 *   purpose — so a new page breaks CI in the repo it was added in.
 * - Both repos' `AGENTS.md` say to update this file with any page, tab or label change.
 *
 * The roles mirror `sprintstart-frontend/src/auth/accessPolicy.ts`; when that file changes, this
 * one has to follow.
 *
 * Deliberately no page for the login screen, the skill wizard or the 404: nobody asks the buddy
 * how to reach them, and every entry costs prompt space on every question that does.
 */
object AppGuide {
    /** Not a page: the project selector every project-scoped page follows. */
    const val PROJECT_SWITCHER =
        "Most pages show the project selected in the account menu at the bottom of the sidebar " +
            "(click your avatar, then \"Switch project\"). A page that looks empty or shows the " +
            "wrong team is usually showing a different project."

    /** Not a page either: the buddy itself, which lives on every page. */
    const val BUDDY_EVERYWHERE =
        "The buddy is the floating window in the corner of every page (drag it to another " +
            "corner), and also a full page at /buddy next to the Chat."

    val pages: List<AppGuidePage> = listOf(
        AppGuidePage(
            name = "Dashboard",
            path = "/",
            whereToFind = "sidebar, \"Dashboard\" (the start page)",
            purpose = "Personal overview: greeting, next step, onboarding progress, skills, " +
                "recent chats and project widgets.",
            howTos = listOf(
                AppGuideHowTo(
                    "change which widgets show",
                    "\"Edit dashboard\" → add or remove widgets (\"Your widgets\"), then \"Done\"; " +
                        "resetting restores the default layout",
                ),
            ),
        ),
        AppGuidePage(
            name = "Board",
            path = "/board",
            whereToFind = "sidebar, \"Board\"",
            purpose = "The hire's own board: the current task, path steps, notes, checklists and " +
                "links (many placed by the buddy), arranged in stages and areas.",
        ),
        AppGuidePage(
            name = "Chat",
            path = "/chat",
            whereToFind = "sidebar, \"Chat\"",
            purpose = "Ask the AI questions about the project's documentation and code; answers " +
                "cite their sources. Separate from the buddy, which also knows the person's " +
                "own onboarding.",
            matches = listOf("/chat/"),
        ),
        AppGuidePage(
            name = "Buddy",
            path = "/buddy",
            whereToFind = "the Buddy tab next to Chat, or the floating buddy on any page",
            purpose = "The onboarding buddy as a full page.",
        ),
        AppGuidePage(
            name = "Knowledge Base",
            path = "/knowledge-base",
            whereToFind = "sidebar, \"Knowledge Base\"",
            purpose = "Browse the project's ingested documentation, code runbooks and artifacts.",
        ),
        AppGuidePage(
            name = "OnBoarding",
            path = "/onboarding",
            whereToFind = "sidebar, \"OnBoarding\" (shown until their onboarding is finished)",
            purpose = "The person's onboarding path: phases, steps and knowledge-check questions, " +
                "in the order they unlock.",
            howTos = listOf(
                AppGuideHowTo(
                    "work through a step",
                    "open the step in the current phase → \"Start step\", and \"Mark as complete\" " +
                        "when finished",
                ),
                AppGuideHowTo(
                    "get help with a step or a question",
                    "\"Ask your buddy about this\" on it brings it into the buddy chat",
                ),
            ),
            matches = listOf("/onboarding/"),
        ),
        AppGuidePage(
            name = "Settings",
            path = "/settings",
            whereToFind = "the gear / account menu at the bottom of the sidebar, \"Settings\"",
            purpose = "Tabs \"User Profile\" (name, profile icon, GitHub username), \"Appearance\" " +
                "(theme) and, for project managers, HR and admins, \"Access Tokens\".",
            howTos = listOf(
                AppGuideHowTo(
                    "set a GitHub username so pull requests are found",
                    "Settings → \"User Profile\"",
                ),
            ),
        ),
        AppGuidePage(
            name = "PM Dashboard",
            path = "/pm-dashboard",
            whereToFind = "sidebar, \"PM Dashboard\" — one page with the tabs \"Overview\", \"Team\" " +
                "(Members, Roles), \"Onboarding\", \"Questions\", \"Knowledge gaps\" and \"Escalations\"",
            purpose = "The selected project at a glance (the \"Overview\" tab): who needs attention, " +
                "ingestion status and the project analysis. The other tabs are listed separately.",
            roles = MANAGERS,
            managerScoped = true,
        ),
        AppGuidePage(
            name = "Team",
            path = "/team-management",
            whereToFind = "$PM_TAB \"Team\" tab, with the views \"Members\" and \"Roles\"",
            purpose = "\"Members\": everybody on the project and their onboarding progress. \"Roles\": " +
                "the project's roles and the skills each needs.",
            howTos = listOf(
                AppGuideHowTo(
                    "create a project role",
                    "Team → \"Roles\" → \"Create role\" (name and what it is responsible for)",
                    link = "/team-management?tab=roles",
                ),
                AppGuideHowTo(
                    "give a role its skills",
                    "Team → \"Roles\" → pick the role → \"Skills\": add one, or \"Suggest skills\"",
                    link = "/team-management?tab=roles",
                ),
                AppGuideHowTo(
                    "assign members to a role",
                    "Team → \"Roles\" → pick the role → \"Members\"; \"Without a role\" lists who has " +
                        "none yet",
                    link = "/team-management?tab=roles",
                ),
                AppGuideHowTo(
                    "look at one member",
                    "Team → \"Members\" → click the person for a side panel, \"Full profile\" for " +
                        "their page",
                    link = "/team-management",
                ),
            ),
            roles = MANAGERS,
            managerScoped = true,
        ),
        AppGuidePage(
            name = "Team member",
            path = "/team/{member_id}",
            whereToFind = "$PM_TAB \"Team\" tab → \"Members\" → a person → \"Full profile\"",
            purpose = "One member's full profile: their roles, current step, onboarding path, " +
                "feedback and skip requests, and skill and knowledge gaps.",
            howTos = listOf(
                AppGuideHowTo(
                    "choose or change a member's role",
                    "under their name: the \"Choose role\" / \"Add role\" picker adds one, the × on a " +
                        "role removes it",
                ),
                AppGuideHowTo(
                    "rebuild a member's onboarding path with AI",
                    "\"Rebuild path\" in the member's header",
                    doers = PM_ADMIN,
                ),
            ),
            roles = MANAGERS,
            managerScoped = true,
            matches = listOf("/team/"),
        ),
        AppGuidePage(
            name = "Onboarding (PM)",
            path = "/insights/onboarding",
            whereToFind = "$PM_TAB \"Onboarding\" tab",
            purpose = "Contribution metrics per hire: pull requests, review waits, stalls.",
            roles = MANAGERS,
            managerScoped = true,
        ),
        AppGuidePage(
            name = "Questions",
            path = "/insights/faq",
            whereToFind = "$PM_TAB \"Questions\" tab",
            purpose = "The questions the team asks most, grouped.",
            roles = MANAGERS,
            managerScoped = true,
            matches = listOf("/insights/faq/"),
        ),
        AppGuidePage(
            name = "Knowledge gaps",
            path = "/insights/knowledge-gaps",
            whereToFind = "$PM_TAB \"Knowledge gaps\" tab",
            purpose = "Topics the documentation does not cover well enough, from what people asked.",
            roles = MANAGERS,
            managerScoped = true,
            matches = listOf("/insights/knowledge-gaps/"),
        ),
        AppGuidePage(
            name = "Escalations",
            path = "/insights/knowledge-requests",
            whereToFind = "$PM_TAB \"Escalations\" tab, with the views \"Open\" and \"Durable answers\"",
            purpose = "Questions hires flagged because the buddy could not answer them, and the " +
                "answers already written for them.",
            howTos = listOf(
                AppGuideHowTo(
                    "answer a hire's escalated question",
                    "Escalations → \"Open\" → open it and answer; the answer becomes team knowledge " +
                        "the buddy uses from then on",
                    doers = PM_ADMIN,
                    link = "/insights/knowledge-requests",
                ),
                AppGuideHowTo(
                    "see answers already written",
                    "Escalations → \"Durable answers\"",
                    link = "/insights/knowledge-requests?view=answered",
                ),
            ),
            roles = MANAGERS,
            managerScoped = true,
        ),
        AppGuidePage(
            name = "Data Ingestion",
            path = "/data-ingestion",
            whereToFind = "sidebar, \"Data Ingestion\" (the project manager section)",
            purpose = "The material the AI and the buddy answer from, for the selected project, " +
                "and whether it is ingested yet.",
            howTos = listOf(
                AppGuideHowTo(
                    "connect GitHub, Jira, Confluence or upload documents",
                    "\"Add sources\" → pick the kind → \"Connect\"",
                ),
            ),
            roles = MANAGERS,
            managerScoped = true,
        ),
        AppGuidePage(
            name = "Blueprints",
            path = "/blueprints",
            whereToFind = "sidebar, \"Blueprints\" (the project manager section)",
            purpose = "Reusable onboarding path templates (\"Project blueprints\", and for admins " +
                "\"Global blueprints\"). Editing one never changes a hire's live path.",
            howTos = listOf(
                AppGuideHowTo(
                    "create an onboarding blueprint",
                    "\"New blueprint path\", then add phases and steps in the \"Graph\" or \"Outline\" " +
                        "view",
                ),
                AppGuideHowTo(
                    "see earlier versions of a blueprint",
                    "open it → \"Version history\"",
                ),
            ),
            roles = MANAGERS,
            managerScoped = true,
            matches = listOf("/blueprints/"),
        ),
        AppGuidePage(
            name = "Hire Setup",
            path = "/hire-setup",
            whereToFind = "sidebar, \"Hire Setup\" (the project manager section), tabs \"Arrival\" " +
                "and \"Starter work\"",
            purpose = "What a new hire needs before they start (accounts, access, setup) and the " +
                "first work waiting for them.",
            howTos = listOf(
                AppGuideHowTo(
                    "write the arrival checklist (accounts, access, setup)",
                    "Hire Setup → \"Arrival\" tab",
                    doers = PM_ADMIN,
                    link = "/hire-setup?tab=arrival",
                ),
                AppGuideHowTo(
                    "review the first tasks offered to hires",
                    "Hire Setup → \"Starter work\" tab",
                    doers = PM_ADMIN,
                    link = "/hire-setup?tab=starter",
                ),
            ),
            roles = MANAGERS,
        ),
        AppGuidePage(
            name = "Access Management",
            path = "/admin",
            whereToFind = "sidebar, \"Access Management\", tabs \"Users\", \"Projects\", \"Skills\" " +
                "(admins only) and \"Tokens\"",
            purpose = "Users, projects, the organisation-wide skill pool and access tokens.",
            howTos = listOf(
                AppGuideHowTo(
                    "create a project",
                    "\"Projects\" tab → \"New Project\": Details (name, description, industry, " +
                        "project manager) → Members → Sources → Review",
                    doers = ADMIN_ONLY,
                    link = "/admin?tab=projects",
                ),
                AppGuideHowTo(
                    "make somebody a project's manager, or add people to a project",
                    "\"Projects\" tab → open the project",
                    doers = ADMIN_ONLY,
                    link = "/admin?tab=projects",
                ),
                AppGuideHowTo(
                    "change somebody's permission group (member, PM, HR, admin)",
                    "\"Users\" tab → open the user",
                    doers = ADMIN_ONLY,
                    link = "/admin?tab=users",
                ),
                AppGuideHowTo(
                    "add, edit or retire a skill in the organisation-wide pool",
                    "\"Skills\" tab → \"New skill\", or open a skill to edit it or \"Retire skill\"; " +
                        "taking a skill off one role is done in Team → \"Roles\" instead",
                    doers = ADMIN_ONLY,
                    link = "/admin?tab=skills",
                ),
            ),
            roles = ADMIN_HR,
        ),
    )

    /** The page [pathname] belongs to, or null for a route this guide does not describe. */
    fun pageAt(pathname: String): AppGuidePage? =
        // Detail routes first: `/team/…` must not be read as Team's own path.
        pages.firstOrNull { page -> page.matches.any { pathname.startsWith(it) } }
            ?: pages.firstOrNull { it.path == pathname }
}
