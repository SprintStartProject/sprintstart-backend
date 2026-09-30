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
 */
data class AppGuideHowTo(
    val task: String,
    val steps: String,
    val doers: Set<Role>? = null,
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

/**
 * The map of the app the buddy reads from, so "where do I…" is answered from the app rather than
 * from the project's documentation — which describes the *project*, and has never heard of a page
 * called Team Management.
 *
 * ### Keeping it true
 *
 * This is a hand-written copy of what the frontend shows, and it goes stale the moment a page is
 * renamed there. Two things keep that visible rather than silent: the labels are the UI's own
 * (sidebar entries, tab names, button text — quoted, so a rename is a grep away), and
 * `AppGuideTest` pins the route list, so adding or removing a page here is a deliberate edit.
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
            ),
            matches = listOf("/onboarding/"),
        ),
        AppGuidePage(
            name = "Settings",
            path = "/settings",
            whereToFind = "the gear / account menu at the bottom of the sidebar, \"Settings\"",
            purpose = "Tabs \"User Profile\" (name, profile icon, GitHub username), \"Appearance\" " +
                "(theme) and \"Access Tokens\".",
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
            whereToFind = "sidebar, \"PM Dashboard\" (the project manager section)",
            purpose = "The selected project at a glance: ingestion status, \"Team overview\", and " +
                "\"Insights\" (recurring questions, knowledge gaps, onboarding metrics).",
            howTos = listOf(
                AppGuideHowTo(
                    "see how the team's onboarding is going",
                    "the \"Team overview\" card; click it for Team Management",
                ),
            ),
            roles = MANAGERS,
            managerScoped = true,
        ),
        AppGuidePage(
            name = "Team Management",
            path = "/team-management",
            whereToFind = "PM Dashboard → the \"Team overview\" card (it has no sidebar entry)",
            purpose = "Tabs \"User Management\" (every member and their onboarding progress) and " +
                "\"Role Management\" (the project's roles and the skills each needs).",
            howTos = listOf(
                AppGuideHowTo(
                    "create a project role",
                    "\"Role Management\" tab → \"Create role\" (name and what it is responsible for)",
                ),
                AppGuideHowTo(
                    "give a role its skills",
                    "\"Role Management\" → pick the role → \"Skills\": add one, or \"Suggest skills\"",
                ),
                AppGuideHowTo(
                    "assign members to a role",
                    "\"Role Management\" → pick the role → \"Members\"; \"Without a role\" lists who " +
                        "has none yet",
                ),
                AppGuideHowTo(
                    "open one member",
                    "\"User Management\" tab → click the person",
                ),
            ),
            roles = MANAGERS,
            managerScoped = true,
        ),
        AppGuidePage(
            name = "Team member",
            path = "/team/{member_id}",
            whereToFind = "Team Management → \"User Management\" → click a person",
            purpose = "One member: their roles, current step, onboarding path, feedback and skip " +
                "requests, and skill and knowledge gaps.",
            howTos = listOf(
                AppGuideHowTo(
                    "choose or change a member's role",
                    "click their role chip (or \"Choose role\" when they have none) under their name " +
                        "→ \"Manage Roles\"",
                ),
                AppGuideHowTo(
                    "rebuild a member's onboarding path with AI",
                    "the rebuild button in the member's header",
                    doers = PM_ADMIN,
                ),
            ),
            roles = MANAGERS,
            managerScoped = true,
            matches = listOf("/team/"),
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
            purpose = "Reusable onboarding path templates (\"Global blueprints\" and \"Project " +
                "blueprints\"). Editing one never changes a hire's live path.",
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
            whereToFind = "sidebar, \"Hire Setup\" (the project manager section)",
            purpose = "What a new hire needs before they start and the first work waiting for them: " +
                "tabs \"Arrival steps\" (/hire-setup?tab=arrival) and \"Starter work\" " +
                "(/hire-setup?tab=starter).",
            howTos = listOf(
                AppGuideHowTo(
                    "write the arrival checklist (accounts, access, setup)",
                    "Hire Setup → \"Arrival steps\" tab",
                    doers = PM_ADMIN,
                ),
                AppGuideHowTo(
                    "review the first tasks offered to hires",
                    "Hire Setup → \"Starter work\" tab",
                    doers = PM_ADMIN,
                ),
            ),
            roles = MANAGERS,
        ),
        AppGuidePage(
            name = "Escalation Inbox",
            path = "/insights/knowledge-requests",
            whereToFind = "sidebar, \"Escalation Inbox\" (the project manager section)",
            purpose = "Questions hires flagged because the buddy could not answer them.",
            howTos = listOf(
                AppGuideHowTo(
                    "answer a hire's escalated question",
                    "open it in the inbox and answer; the answer becomes team knowledge the buddy " +
                        "uses from then on",
                    doers = PM_ADMIN,
                ),
            ),
            roles = MANAGERS,
            managerScoped = true,
        ),
        AppGuidePage(
            name = "FAQ",
            path = "/insights/faq",
            whereToFind = "PM Dashboard → \"Insights\" → the FAQ widget",
            purpose = "The questions the team asks most, grouped.",
            roles = MANAGERS,
            managerScoped = true,
            matches = listOf("/insights/faq/"),
        ),
        AppGuidePage(
            name = "Knowledge gaps",
            path = "/insights/knowledge-gaps",
            whereToFind = "PM Dashboard → \"Insights\" → the knowledge gaps widget",
            purpose = "Topics the documentation does not cover well enough, from what people asked.",
            roles = MANAGERS,
            managerScoped = true,
            matches = listOf("/insights/knowledge-gaps/"),
        ),
        AppGuidePage(
            name = "Onboarding metrics",
            path = "/insights/onboarding",
            whereToFind = "PM Dashboard → \"Insights\" → the onboarding metrics widget",
            purpose = "Contribution metrics per hire: pull requests, review waits, stalls.",
            roles = MANAGERS,
            managerScoped = true,
        ),
        AppGuidePage(
            name = "Access Management",
            path = "/admin",
            whereToFind = "sidebar, \"Access Management\"",
            purpose = "Tabs \"Users\", \"Projects\" and \"Tokens\" for the whole organisation.",
            howTos = listOf(
                AppGuideHowTo(
                    "create a project",
                    "\"Projects\" tab → \"New Project\": Details (name, description, industry, " +
                        "project manager) → Members → Sources → Review",
                    doers = ADMIN_ONLY,
                ),
                AppGuideHowTo(
                    "make somebody a project's manager, or add people to a project",
                    "\"Projects\" tab → open the project",
                    doers = ADMIN_ONLY,
                ),
                AppGuideHowTo(
                    "change somebody's permission group (member, PM, HR, admin)",
                    "\"Users\" tab → open the user",
                    doers = ADMIN_ONLY,
                ),
            ),
            roles = ADMIN_HR,
        ),
    )

    /** The page [pathname] belongs to, or null for a route this guide does not describe. */
    fun pageAt(pathname: String): AppGuidePage? =
        // Detail routes first: `/team/…` must not be read as Team Management's own path.
        pages.firstOrNull { page -> page.matches.any { pathname.startsWith(it) } }
            ?: pages.firstOrNull { it.path == pathname }
}
