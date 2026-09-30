package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The buddy's knowledge of the app itself: which pages the caller can open, what is on them, how to
 * do things there, and who to ask for what they cannot do.
 *
 * Before this the buddy had one place to look for "how do I create a project?" — `search_docs` —
 * and that corpus is the *project's* material, which has never heard of Sprintstart's pages. It
 * either said nothing useful or described a screen that does not exist.
 *
 * Mounted for everybody, in both the hire's buddy and team mode: everybody uses the app, and what
 * changes with the reader is what the guide says, not whether it exists.
 *
 * ### Read for the caller, never for somebody else
 *
 * The guide is filtered by the caller's own permission group and, for a PM, by the projects they
 * actually manage — the same two facts the frontend's route guard decides on. A page the caller
 * cannot open is never offered as a place to go; what happens there is listed as somebody else's
 * job, with who that somebody is. Telling a PM "go to Access Management → New Project" sends them to
 * a page their sidebar does not have.
 *
 * This is guidance, not authorisation: every page still enforces its own access, so a guide that
 * drifted from the frontend would mislead, never let anybody in.
 */
@Component
class BuddyAppGuideTools(
    private val userApi: UserApi,
) {
    /**
     * The guide for [userId], naming the page they are on when [currentPage] is known.
     *
     * @param currentPage The path the caller's browser was on when they spoke (query and fragment
     * are ignored). Whatever the client sent, so it is only ever matched against the guide, never
     * echoed back verbatim.
     */
    fun guideFor(userId: UUID, currentPage: String?): String {
        val role = userApi.getPermissionGroup(userId) ?: Role.USER
        val managed = if (role == Role.PM) managedProjectNames(userId) else emptyList()
        val reader = Reader(role, managesAProject = role != Role.PM || managed.isNotEmpty())

        val openable = AppGuide.pages.filter { reader.opens(it) }
        val elsewhere = AppGuide.pages.flatMap { page ->
            page.howTos
                .filter { !reader.opens(page) || (it.doers != null && role !in it.doers) }
                .map { page to it }
        }

        return buildString {
            appendLine(whoIsAsking(role, managed))
            here(currentPage)?.let { appendLine(it) }
            appendLine()
            appendLine("Pages they can open (link each as [Name](path)):")
            openable.forEach { page -> appendPage(page, role) }
            if (elsewhere.isNotEmpty()) {
                appendLine()
                appendLine("Only somebody else can do these — say who to ask, never send them to the page:")
                elsewhere.forEach { (page, howTo) ->
                    val who = describeDoers(howTo.doers ?: page.roles)
                    appendLine("- ${howTo.task}: $who, on ${page.name} (${howTo.steps})")
                }
            }
            if (reader.blockedFromManagerPages()) {
                appendLine()
                appendLine(
                    "They hold the PM role but manage no project, so the manager pages (PM Dashboard, " +
                        "Team Management, Data Ingestion, Blueprints, the insights) stay hidden. An admin " +
                        "makes somebody a project's manager in Access Management → Projects.",
                )
            }
            appendLine()
            appendLine("Anywhere in the app:")
            appendLine("- ${AppGuide.PROJECT_SWITCHER}")
            append("- ${AppGuide.BUDDY_EVERYWHERE}")
        }
    }

    private fun StringBuilder.appendPage(page: AppGuidePage, role: Role) {
        val link = if (page.needsId) "${page.path} — fill the id in from another tool" else page.path
        appendLine("- ${page.name} ($link) — ${page.whereToFind}")
        appendLine("    ${page.purpose}")
        page.howTos
            .filter { it.doers == null || role in it.doers }
            .forEach { appendLine("    · to ${it.task}: ${it.steps}") }
    }

    /** The names of the caller's projects that they manage — the PM-only gate on manager pages. */
    private fun managedProjectNames(userId: UUID): List<String> {
        val authId = userApi.getAuthIdByUserId(userId).orElse(null) ?: return emptyList()
        return userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()
            .filter { userApi.canManageProject(authId, it.projectId) }
            .map { it.name }
    }

    private fun whoIsAsking(role: Role, managed: List<String>): String {
        val base = "Who is asking: ${ROLE_NAMES.getValue(role)}."
        return when {
            role != Role.PM -> base
            managed.isEmpty() -> "$base They manage no project yet."
            else -> "$base They manage: ${managed.joinToString(", ")}."
        }
    }

    /** The page the caller is looking at, or null when nothing usable was sent. */
    private fun here(currentPage: String?): String? {
        val pathname = currentPage
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.takeIf { it.startsWith("/") && it.length <= MAX_PATH_LENGTH }
            ?: return null
        val page = AppGuide.pageAt(pathname)
            ?: return "They are on a page this guide does not describe."
        return "They are looking at: ${page.name} (${page.path}). \"This page\" or \"here\" means this one."
    }

    private fun describeDoers(doers: Set<Role>): String {
        val names = DOER_ORDER.filter { it in doers }.map { DOER_NAMES.getValue(it) }
        if (names.size <= 1) return names.joinToString()
        return names.dropLast(1).joinToString(", ") + " or " + names.last()
    }

    /** The two facts the frontend's route guard decides a page on. */
    private class Reader(
        val role: Role,
        val managesAProject: Boolean,
    ) {
        fun opens(page: AppGuidePage): Boolean =
            role in page.roles && (!page.managerScoped || role != Role.PM || managesAProject)

        fun blockedFromManagerPages(): Boolean = role == Role.PM && !managesAProject
    }

    companion object {
        const val GET_APP_GUIDE = "get_app_guide"

        // A path is a few segments and an id; anything longer is not a route of this app.
        private const val MAX_PATH_LENGTH = 200

        private val ROLE_NAMES = mapOf(
            Role.USER to "a team member",
            Role.PM to "a project manager (PM)",
            Role.HR to "HR",
            Role.ADMIN to "an admin",
        )

        // Who does it, named the way somebody would go and find them. A PM does manager work for the
        // projects they manage, so "the project's manager" is the person to chase, not "a PM".
        private val DOER_ORDER = listOf(Role.PM, Role.HR, Role.ADMIN, Role.USER)
        private val DOER_NAMES = mapOf(
            Role.PM to "the project's manager",
            Role.HR to "HR",
            Role.ADMIN to "an admin",
            Role.USER to "anybody",
        )

        val GET_APP_GUIDE_SPEC = BuddyToolSpecDto(
            name = GET_APP_GUIDE,
            description = "How the Sprintstart app itself works for the person you are talking to: the " +
                "pages they can open, where each one is (the sidebar entry or the card that leads " +
                "there), what it is for and how to do things on it — create a project role, choose a " +
                "member's role, connect a source, write arrival steps, set a GitHub username — plus " +
                "who to ask for what they cannot do themselves, and which page they are looking at " +
                "right now. Call it for any 'where do I…', 'how do I … here', 'where is …' or " +
                "'what is this page' question about the app. `search_docs` describes the project the " +
                "team works on, not this app, so never answer these from it. Takes no arguments — it " +
                "always reads the caller.",
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject { })
            },
        )
    }
}
