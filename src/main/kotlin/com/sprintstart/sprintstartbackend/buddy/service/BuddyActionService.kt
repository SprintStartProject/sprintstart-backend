package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyActionResponse
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.HireAction
import com.sprintstart.sprintstartbackend.assistant.external.HireActionContext
import com.sprintstart.sprintstartbackend.assistant.external.HireActionOutcome
import com.sprintstart.sprintstartbackend.assistant.external.HireActionRequest
import com.sprintstart.sprintstartbackend.assistant.external.HireActionScope
import com.sprintstart.sprintstartbackend.assistant.external.HireProject
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * The buddy's *action* tools: the buddy stops only advising and starts doing — always on the hire's
 * explicit confirmation.
 *
 * The one rule that makes this safe: a tool call never mutates. When the AI reasoner calls an
 * action tool during a turn, [propose] hands back a proposal the frontend renders as a confirm button —
 * nothing changes. Only [perform], reached by a separate confirm request after the hire clicks, runs the
 * real operation.
 *
 * Every action is a [HireAction] bean a feature contributes from its `assistant/` package; this class only
 * mounts them and puts the project gate in front. A project-scoped action runs on the caller's one project,
 * re-resolved server-side at confirm time — a client can never confirm an action against a project the
 * buddy did not scope it to, nor act as another hire.
 */
@Service
class BuddyActionService(
    private val userApi: UserApi,
    private val hireActions: List<HireAction>,
) {
    /**
     * The action tools the AI reasoner is told it may propose, alongside the read-only tools.
     *
     * Per hire rather than globally, for the same reason [BuddyToolExecutor.toolSpecs] is: the
     * path actions have a subject that may not exist. A mentor handed `complete_step` for somebody
     * with no onboarding path will offer to tick a step off a plan they have not got.
     */
    fun actionSpecs(userId: UUID): List<BuddyToolSpecDto> =
        hireActions.filter { it.isMountedFor(UserId(userId)) }.map { it.spec }

    /** Whether [toolName] is an action tool (handled by [propose]) rather than a read-only tool. */
    fun isAction(toolName: String): Boolean = hireActions.any { it.spec.name == toolName }

    /**
     * Turns an action tool call into a proposal for the hire to confirm — without mutating.
     *
     * Returns the [HireActionOutcome.toolResult] to feed back to the AI (so it relays "I can do X —
     * confirm below", never "done"), and a [HireActionOutcome.proposal] to emit to the client when the
     * action can be offered. When it can't (no project, or a missing argument), there is no proposal
     * and the tool result is the legible reason.
     */
    fun propose(call: BuddyToolCallDto, userId: UUID): HireActionOutcome {
        val action = hireActions.firstOrNull { it.spec.name == call.name }
            ?: return HireActionOutcome("Unknown action: ${call.name}.", null)
        val hire = UserId(userId)
        val project = if (action.scope == HireActionScope.PERSON) {
            null
        } else {
            when (val resolution = resolveProject(userId)) {
                is ProjectResolution.Resolved -> resolution.project
                ProjectResolution.None -> return HireActionOutcome(
                    "The hire is not on a project yet, so there is nothing to ${action.gerund} for them.",
                    null,
                )
                ProjectResolution.Ambiguous -> return HireActionOutcome(
                    "The hire is onboarding on more than one project. Ask which one before offering to " +
                        "${action.gerund}.",
                    null,
                )
            }
        }
        return action.propose(call, HireActionContext(hire, project))
    }

    /**
     * Runs a confirmed action on behalf of [jwt]'s user, scoped to their re-resolved project.
     *
     * Never throws for a handled outcome: an expected precondition failure ("no current task",
     * "not a member") comes back as `ok = false` with a legible message, so the buddy always has a
     * line to relay. Only a genuinely unexpected failure propagates. The action runs on the IO dispatcher.
     */
    suspend fun perform(request: HireActionRequest, jwt: Jwt): BuddyActionResponse {
        val action = hireActions.firstOrNull { it.spec.name == request.action }
            ?: return BuddyActionResponse(ok = false, message = "That action isn't recognised.")
        val authId = AuthId(jwt.subject)
        val userId = withContext(Dispatchers.IO) { userApi.getUserIdByAuthId(authId.value).orElse(null) }
            ?: return BuddyActionResponse(ok = false, message = "I couldn't find your account.")
        val project = if (action.scope == HireActionScope.PERSON) {
            null
        } else {
            when (val resolution = withContext(Dispatchers.IO) { resolveProject(userId) }) {
                is ProjectResolution.Resolved -> resolution.project
                ProjectResolution.None -> return BuddyActionResponse(
                    ok = false,
                    message = "You're not on a project yet.",
                )
                ProjectResolution.Ambiguous -> return BuddyActionResponse(
                    ok = false,
                    message = "You're onboarding on more than one project — tell me which one and I'll set it up.",
                )
            }
        }
        val context = HireActionContext(UserId(userId), project)
        return try {
            withContext(Dispatchers.IO) { action.perform(request, context, authId) }
        } catch (ex: ResponseStatusException) {
            // A precondition the underlying route enforces (not a member, blank question, …). Relay
            // its reason rather than failing the whole confirm with an HTTP error.
            BuddyActionResponse(ok = false, message = ex.reason ?: "That didn't go through — try again in a moment.")
        }
    }

    private fun resolveProject(userId: UUID): ProjectResolution {
        val projects = userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()
        return when {
            projects.isEmpty() -> ProjectResolution.None
            projects.size > 1 -> ProjectResolution.Ambiguous
            else -> projects.first().let { ProjectResolution.Resolved(HireProject(ProjectId(it.projectId), it.name)) }
        }
    }

    private sealed interface ProjectResolution {
        data class Resolved(
            val project: HireProject,
        ) : ProjectResolution

        data object None : ProjectResolution

        data object Ambiguous : ProjectResolution
    }
}
