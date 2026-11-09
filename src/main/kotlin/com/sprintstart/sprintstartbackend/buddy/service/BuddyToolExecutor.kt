package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.HireTool
import com.sprintstart.sprintstartbackend.assistant.external.HireToolContext
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Runs the backend-owned tools the buddy agent may call, and describes them to the AI reasoner.
 *
 * The buddy answers corpus questions AI-side (``search_docs``); tools here answer questions about
 * the hire's *own* state -- their onboarding path, their setup, their work -- which only the backend
 * can see. Each tool is executed strictly on behalf of the resolved caller — the agent never
 * supplies whose data to read, so one hire can never read another's metrics through the buddy.
 *
 * Every tool comes from a feature as a [HireTool] bean, mounted on its own condition and in its own
 * order, the path first. This only mounts, snapshots and dispatches them.
 */
@Component
class BuddyToolExecutor(
    private val hireTools: List<HireTool>,
) {
    /**
     * The backend tools the AI reasoner is told it may call, for this hire.
     *
     * Mounted per hire, not globally, wherever a tool has a subject that may not exist: a tool
     * that can only ever answer "there is nothing" invites the mentor to raise it anyway.
     *
     * The gate is always the subject's own emptiness, asked of the service that owns it — an
     * arrival list nobody authored, a hire with nothing left to place. Never the hire's role: every
     * tool that has something to say is offered to everybody, and what a hire is asked about is
     * decided by what is actually there for them.
     *
     * That rule covers the caller's own buddy. Team mode ([BuddyTeamTools]) is the one exception, and
     * a deliberate one: a project's manager is handed tools that read *other people* on that project.
     * It is gated on managing the project — checked by [BuddyTeamService] before every turn — and every
     * team tool re-checks that the person it reads is on that project. None of these tools is ever
     * mounted here.
     */
    fun toolSpecs(userId: UUID): List<BuddyToolSpecDto> =
        hireTools.filter { it.isMountedFor(UserId(userId)) }.map { it.spec }

    /**
     * A plain-text snapshot of the hire's own state, for the buddy's opening greeting to ground
     * itself in. Reuses the exact reads the caller-scoped tools expose, so the opener and the tools
     * can never describe different states.
     *
     * The path comes first, and the order is the feature: the onboarding is what a greeting opens
     * on. Setup comes next, ahead of how their work is going -- a greeting grounded in progress
     * before setup greets a hire who cannot get in with a good first issue. Each part is omitted
     * entirely when it has nothing: a greeting grounded in "arrival: nothing" will find something to
     * say about it.
     */
    fun stateSnapshot(userId: UUID): String =
        hireTools
            .sortedBy { it.snapshotPosition }
            .mapNotNull { it.snapshotFor(UserId(userId)) }
            .joinToString("\n\n")

    /**
     * Executes [call] on behalf of [userId], returning a plain-text result for the model.
     *
     * @param currentPage The app path the caller was on when they spoke, for the app guide.
     */
    fun execute(call: BuddyToolCallDto, userId: UUID, currentPage: String? = null): String =
        hireTools
            .firstOrNull { it.spec.name == call.name }
            ?.execute(call, HireToolContext(UserId(userId), currentPage))
            ?: "Unknown tool: ${call.name}."
}
