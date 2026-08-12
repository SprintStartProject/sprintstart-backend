package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyActionType
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.BuddyActionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.verification.SubmitVerificationAttemptRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyActionResponse
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * The buddy's *action* tools: the buddy stops only advising and starts doing — always on the hire's
 * explicit confirmation.
 *
 * The one rule that makes this safe: **a tool call never mutates.** When the AI reasoner calls an
 * action tool during a turn, [propose] resolves the concrete action and hands back a proposal the
 * frontend renders as a confirm button — nothing changes. Only [perform], reached by a separate
 * confirm request after the hire clicks, runs the real `/me/...` operation. Both ends read the same
 * [BuddyActionType] catalog, so the proposal and the execution can never name different actions.
 *
 * Every action is executed strictly on behalf of the resolved caller and scoped to the caller's own
 * project, re-resolved server-side at confirm time — a client can never confirm an action against a
 * project the buddy did not scope it to, nor act as another hire.
 */
@Service
@Suppress("TooManyFunctions") // Six wrapped actions, each with a propose + a perform helper.
class BuddyActionService(
    private val taskZeroService: TaskZeroService,
    private val taskOrientationService: TaskOrientationService,
    private val knowledgeBaseService: KnowledgeBaseService,
    private val userGoalService: UserGoalService,
    private val verificationService: VerificationService,
    private val userApi: UserApi,
    private val attestationService: AttestationService,
    private val boardService: BoardService,
) {
    /** The action tools the AI reasoner is told it may propose, alongside the read-only tools. */
    fun actionSpecs(): List<BuddyToolSpecDto> =
        listOf(
            FLAG_TO_PM_SPEC,
            CLAIM_TASK_ZERO_SPEC,
            OPEN_ORIENTATION_SPEC,
            CLAIM_GOAL_SPEC,
            REQUEST_ATTESTATION_SPEC,
            SUBMIT_VERIFICATION_SPEC,
            SET_GITHUB_LOGIN_SPEC,
        )

    /** Whether [toolName] is an action tool (handled by [propose]) rather than a read-only tool. */
    fun isAction(toolName: String): Boolean = BuddyActionType.fromToolName(toolName) != null

    /**
     * Turns an action tool call into a proposal for the hire to confirm — **without mutating**.
     *
     * Returns the [ProposeOutcome.toolResult] to feed back to the AI (so it relays "I can do X —
     * confirm below", never "done"), and a [ProposeOutcome.proposal] to emit to the client when the
     * action can be offered. When it can't (no project, or a missing question), there is no proposal
     * and the tool result is the legible reason.
     */
    fun propose(call: BuddyToolCallDto, userId: UUID): ProposeOutcome {
        val type = BuddyActionType.fromToolName(call.name)
            ?: return ProposeOutcome("Unknown action: ${call.name}.", null)

        // ⚠️ Before the project gate, deliberately. A GitHub login is a fact about a *person*, not
        // about a project -- the same reason `GET /me/arrival` is not project-scoped. Gating it
        // would refuse exactly the hire most likely to be setting one: somebody on day one who has
        // not been added to anything yet. A test pins that.
        if (type == BuddyActionType.SET_GITHUB_LOGIN) {
            return proposeGithubLogin(call, type)
        }

        val project = when (val resolution = resolveProject(userId)) {
            is ProjectResolution.Resolved -> resolution
            ProjectResolution.None ->
                return ProposeOutcome(
                    "The hire is not on a project yet, so there is nothing to ${type.gerund()} for them.",
                    null,
                )
            ProjectResolution.Ambiguous ->
                return ProposeOutcome(
                    "The hire is onboarding on more than one project. Ask which one before offering to " +
                        "${type.gerund()}.",
                    null,
                )
        }

        return when (type) {
            BuddyActionType.FLAG_TO_PM -> {
                val question = call.stringArg("question").trim()
                if (question.isBlank()) {
                    ProposeOutcome(
                        "No question was provided to flag. Ask the hire what they want answered first.",
                        null,
                    )
                } else {
                    proposed(type, project.name, question = question)
                }
            }
            BuddyActionType.CLAIM_GOAL -> {
                val taskId = call.uuidArg("task_id")
                if (taskId == null) {
                    ProposeOutcome(
                        "No task_id was provided. Read the suggested tasks and offer to claim one " +
                            "by its task_id.",
                        null,
                    )
                } else {
                    proposed(type, project.name, question = null, taskId = taskId)
                }
            }
            BuddyActionType.SUBMIT_VERIFICATION -> proposeVerification(call, type, project.name)
            BuddyActionType.REQUEST_ATTESTATION -> proposeAttestation(call, type, project.name)
            else -> proposed(type, project.name, question = null)
        }
    }

    /**
     * Offers to record the GitHub account the hire named, without a project and without mutating.
     *
     * Only the *shape* is checked here — that a username was actually supplied. Whether it is a
     * possible GitHub name, and whether somebody else already claims it, stay with
     * [com.sprintstart.sprintstartbackend.user.service.GithubLoginService] at confirm time: it owns
     * those rules for every other writer, and a second opinion here would be one more place for
     * them to drift.
     */
    private fun proposeGithubLogin(call: BuddyToolCallDto, type: BuddyActionType): ProposeOutcome {
        val login = call.stringArg("login").trim()
        if (login.isBlank()) {
            return ProposeOutcome(
                "No username was provided. Ask the hire for their GitHub username before offering " +
                    "to save it.",
                null,
            )
        }
        return ProposeOutcome(
            toolResult = "Proposed to the hire: save “$login” as their GitHub username. They will " +
                "see a confirm button; it is saved only if they click it. Offer it — do not claim " +
                "it is done.",
            proposal = BuddyActionProposal(
                action = type.toolName,
                label = type.label,
                question = null,
                githubLogin = login,
            ),
        )
    }

    /**
     * Records the confirmed username through the one writer that owns it.
     *
     * ### Why this action exists
     *
     * A hire testing the buddy said *"my GitHub username is …"* and **nothing was recorded**, because
     * no tool could. The model went looking through the corpus for how to store one. Meanwhile the
     * login is what artifact verification compares a pull request's author against, so the failure
     * is not cosmetic: work they really do goes on not being credited, and they read as calm rather
     * than blocked.
     *
     * [com.sprintstart.sprintstartbackend.user.service.GithubLoginService] stays the single writer —
     * the conversation is a second *entry point*, never a second source of truth. That is also what
     * keeps the normalisation, the uniqueness check and the rule that a changed login discards
     * the old verification verdict true here without restating any of them.
     */
    private fun setGithubLogin(authId: String, login: String?): BuddyActionResponse {
        if (login.isNullOrBlank()) {
            return BuddyActionResponse(ok = false, message = "No username was proposed to save.")
        }
        val userId = userApi.getUserIdByAuthId(authId).orElse(null)
            ?: return BuddyActionResponse(ok = false, message = "I couldn't find your account.")

        val saved = userApi.setGithubLogin(userId, login)
        return BuddyActionResponse(
            ok = true,
            message = "Saved — your work will be attributed to @$saved from now on. If that is not " +
                "right, you can change it in Settings any time.",
        )
    }

    /** Same shape as [proposeAttestation]: neither half of a submission may be missing. */
    private fun proposeVerification(
        call: BuddyToolCallDto,
        type: BuddyActionType,
        projectName: String,
    ): ProposeOutcome {
        val moduleId = call.uuidArg("module_id")
        val answer = call.stringArg("answer").trim()
        return when {
            moduleId == null ->
                ProposeOutcome("No module_id was provided. Read the module and submit against its id.", null)
            answer.isBlank() ->
                ProposeOutcome(
                    "No answer was provided. Ask the hire for their answer (a pull request number " +
                        "for an artifact check) before offering to submit.",
                    null,
                )
            else -> proposed(type, projectName, question = null, moduleId = moduleId, answer = answer)
        }
    }

    /**
     * Both halves of an attestation proposal must be real before the hire sees a button: what work,
     * and which teammate. A missing one comes back as guidance the buddy can act on rather than a
     * proposal it cannot honour.
     */
    private fun proposeAttestation(
        call: BuddyToolCallDto,
        type: BuddyActionType,
        projectName: String,
    ): ProposeOutcome {
        val title = call.stringArg("title").trim()
        val attesterId = call.uuidArg("attester_id")
        return when {
            title.isBlank() ->
                ProposeOutcome("No title was provided. Ask the hire what work they want confirmed.", null)
            attesterId == null ->
                ProposeOutcome(
                    "No attester_id was provided. Read get_teammates and ask the hire which of " +
                        "them should confirm it, then pass that person's id.",
                    null,
                )
            else -> proposed(type, projectName, question = null, title = title, attesterId = attesterId)
        }
    }

    /**
     * Runs a confirmed action on behalf of [jwt]'s user, scoped to their re-resolved project.
     *
     * Never throws for a handled outcome: an expected precondition failure ("no eligible Task 0",
     * "not a member") comes back as `ok = false` with a legible message, so the buddy always has a
     * line to relay. Only a genuinely unexpected failure propagates. Blocking work runs on the IO
     * dispatcher; opening orientation is itself suspend and manages its own transactions.
     */
    suspend fun perform(request: BuddyActionRequest, jwt: Jwt): BuddyActionResponse {
        val authId = jwt.subject

        val type = BuddyActionType.fromToolName(request.action)
            ?: return BuddyActionResponse(ok = false, message = "That action isn't recognised.")

        // Same reason as in propose: not project-scoped, so not behind the project gate.
        if (type == BuddyActionType.SET_GITHUB_LOGIN) {
            return try {
                withContext(Dispatchers.IO) { setGithubLogin(authId, request.githubLogin) }
            } catch (ex: ResponseStatusException) {
                // 400 for a syntactically impossible username, 409 when another hire already
                // claims it. Both are things the hire can act on, so they come back as the
                // sentence GithubLoginService wrote rather than as a failed confirm.
                BuddyActionResponse(ok = false, message = ex.reason ?: "That username could not be saved.")
            }
        }

        val context = withContext(Dispatchers.IO) { resolveContext(authId) }
        val resolved = when (context) {
            is CallerContext.Resolved -> context
            CallerContext.NoProject ->
                return BuddyActionResponse(ok = false, message = "You're not on a project yet.")
            CallerContext.MultipleProjects ->
                return BuddyActionResponse(
                    ok = false,
                    message = "You're onboarding on more than one project — tell me which one and I'll set it up.",
                )
        }

        return try {
            dispatch(type, resolved, authId, request)
        } catch (ex: ResponseStatusException) {
            // A precondition the underlying route enforces (not a member, blank question, …). Relay
            // its reason rather than failing the whole confirm with an HTTP error.
            BuddyActionResponse(ok = false, message = ex.reason ?: "That didn't go through — try again in a moment.")
        }
    }

    /** Runs the confirmed action itself. Suspend operations manage their own transactions. */
    private suspend fun dispatch(
        type: BuddyActionType,
        resolved: CallerContext.Resolved,
        authId: String,
        request: BuddyActionRequest,
    ): BuddyActionResponse =
        when (type) {
            BuddyActionType.OPEN_ORIENTATION -> openOrientation(resolved.userId, resolved.projectId)
            BuddyActionType.SUBMIT_VERIFICATION ->
                submitVerification(authId, request.moduleId, request.answer)
            else -> withContext(Dispatchers.IO) {
                when (type) {
                    BuddyActionType.CLAIM_TASK_ZERO -> claimTaskZero(resolved.userId, resolved.projectId)
                    BuddyActionType.FLAG_TO_PM -> flagToPm(authId, resolved.projectId, request.question)
                    BuddyActionType.CLAIM_GOAL ->
                        claimGoal(resolved.userId, authId, resolved.projectId, request.taskId)
                    BuddyActionType.REQUEST_ATTESTATION ->
                        requestAttestation(resolved, request.title, request.attesterId)
                    BuddyActionType.OPEN_ORIENTATION,
                    BuddyActionType.SUBMIT_VERIFICATION,
                    // Not project-scoped, so it returns before the project gate this dispatch
                    // sits behind.
                    BuddyActionType.SET_GITHUB_LOGIN,
                    -> error("handled above")
                }
            }
        }

    private fun claimTaskZero(userId: UUID, projectId: UUID): BuddyActionResponse {
        val result = taskZeroService.getForHire(userId, projectId)
        val task = result.task
        return if (task != null) {
            BuddyActionResponse(
                ok = true,
                message = "Task 0 is yours: “${task.title}”. Open the task packet when you're ready to start.",
            )
        } else {
            BuddyActionResponse(
                ok = false,
                message = "There's no eligible Task 0 to start yet — your PM marks a starter task as Task 0.",
            )
        }
    }

    private suspend fun openOrientation(userId: UUID, projectId: UUID): BuddyActionResponse {
        val orientation = taskOrientationService.getForHire(userId, projectId)
        return if (orientation.packet != null) {
            BuddyActionResponse(
                ok = true,
                message = "I've put together your task orientation for “${orientation.taskTitle}” — " +
                    "the step-by-step, cited guide is right here in our conversation.",
            )
        } else {
            BuddyActionResponse(
                ok = false,
                message = orientation.reason ?: "There's no current task to open a packet for yet.",
            )
        }
    }

    private fun claimGoal(
        userId: UUID,
        authId: String,
        projectId: UUID,
        taskId: UUID?,
    ): BuddyActionResponse {
        if (taskId == null) {
            return BuddyActionResponse(ok = false, message = "No task was proposed to claim.")
        }
        val goal = userGoalService.claimForMe(authId, projectId, taskId)
        // Pin the task the moment it becomes theirs, rather than hoping the mentor thinks to. This
        // conversation is gone by the next visit; the board is what carries "this is what you are
        // working on" across the gap, and the one instant we know for certain it is true is now.
        boardService.place(userId, projectId, BoardCardKind.CURRENT_TASK)
        return BuddyActionResponse(
            ok = true,
            message = "You're now working toward “${goal.title}” — I'll shape your next steps around it. " +
                "It's on your board too, so you won't have to go looking for it.",
        )
    }

    /**
     * Submits the hire's answer to a module's check through the ordinary attempt path — same
     * grading, same ledger write as submitting from a module page. The buddy relays the verdict;
     * it never grades.
     */
    private suspend fun submitVerification(
        authId: String,
        moduleId: UUID?,
        answer: String?,
    ): BuddyActionResponse {
        if (moduleId == null || answer.isNullOrBlank()) {
            return BuddyActionResponse(ok = false, message = "No module or answer was proposed to submit.")
        }
        val result = verificationService.submitModuleAttemptForMe(
            authId,
            moduleId,
            SubmitVerificationAttemptRequest(answer = answer),
        )
        return if (result.passed) {
            BuddyActionResponse(
                ok = true,
                message = "Passed — ${result.feedback} That's on your record now.",
            )
        } else {
            BuddyActionResponse(
                ok = false,
                message = "Not yet — ${result.feedback}" + (result.hint?.let { " $it" } ?: ""),
            )
        }
    }

    /**
     * Asks a named colleague to confirm a piece of the hire's work.
     *
     * The buddy relays; it never confirms. Every rule that makes an attestation worth anything —
     * not the hire, on this project — is enforced by [AttestationService], so a buddy that got the
     * person wrong produces a legible refusal rather than weak evidence.
     */
    private fun requestAttestation(
        resolved: CallerContext.Resolved,
        title: String?,
        attesterId: UUID?,
    ): BuddyActionResponse {
        if (title.isNullOrBlank() || attesterId == null) {
            return BuddyActionResponse(ok = false, message = "I need to know what work to confirm and who to ask.")
        }
        return try {
            val attestation = attestationService.request(
                hireId = resolved.userId,
                projectId = resolved.projectId,
                title = title,
                evidenceUrl = null,
                attesterId = attesterId,
            )
            BuddyActionResponse(
                ok = true,
                message = "Asked them to confirm “${attestation.title}”. " +
                    "It counts once they do — you will see it on your ramp.",
            )
        } catch (e: ResponseStatusException) {
            // A handled precondition ("not on this project", "that is you") is a sentence the buddy
            // can relay, not a stack trace.
            BuddyActionResponse(ok = false, message = e.reason ?: "That request could not be filed.")
        }
    }

    private fun flagToPm(authId: String, projectId: UUID, question: String?): BuddyActionResponse {
        val trimmed = question?.trim().orEmpty()
        if (trimmed.isBlank()) {
            return BuddyActionResponse(ok = false, message = "I need the question to flag — tell me what to ask.")
        }
        knowledgeBaseService.escalate(authId, projectId, trimmed)
        return BuddyActionResponse(
            ok = true,
            message = "Flagged to your PM. Their answer will show up here once they reply.",
        )
    }

    private fun proposed(
        type: BuddyActionType,
        projectName: String,
        question: String?,
        taskId: UUID? = null,
        moduleId: UUID? = null,
        answer: String? = null,
        title: String? = null,
        attesterId: UUID? = null,
        githubLogin: String? = null,
    ): ProposeOutcome =
        ProposeOutcome(
            toolResult = "Proposed to the hire on $projectName: “${type.label}”. They will see a confirm " +
                "button; the action runs only if they click it. Offer it — do not claim it is done.",
            proposal = BuddyActionProposal(
                action = type.toolName,
                label = type.label,
                question = question,
                taskId = taskId,
                moduleId = moduleId,
                answer = answer,
                title = title,
                attesterId = attesterId?.toString(),
                githubLogin = githubLogin,
            ),
        )

    private fun resolveProject(userId: UUID): ProjectResolution {
        val projects = userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()
        return when {
            projects.isEmpty() -> ProjectResolution.None
            projects.size > 1 -> ProjectResolution.Ambiguous
            else -> projects.first().let { ProjectResolution.Resolved(it.projectId, it.name) }
        }
    }

    /** Resolves the caller's id and single onboarding project in one read, for the confirm path. */
    private fun resolveContext(authId: String): CallerContext {
        val userId = resolveUserId(authId)
        return when (val resolution = resolveProject(userId)) {
            is ProjectResolution.Resolved -> CallerContext.Resolved(userId, resolution.projectId, resolution.name)
            ProjectResolution.None -> CallerContext.NoProject
            ProjectResolution.Ambiguous -> CallerContext.MultipleProjects
        }
    }

    private fun resolveUserId(authId: String): UUID =
        userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

    /** Reads a string argument the model passed to a tool, or "" when it is missing/non-text. */
    private fun BuddyToolCallDto.stringArg(name: String): String =
        (arguments[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

    /** Reads a UUID argument the model passed to a tool, or null when it is missing/unparseable. */
    private fun BuddyToolCallDto.uuidArg(name: String): UUID? =
        runCatching { UUID.fromString(stringArg(name)) }.getOrNull()

    /** A verb phrase for the reason lines, e.g. "start Task 0", "flag this to a PM". */
    private fun BuddyActionType.gerund(): String =
        when (this) {
            BuddyActionType.FLAG_TO_PM -> "flag this to a PM"
            BuddyActionType.CLAIM_TASK_ZERO -> "start Task 0"
            BuddyActionType.OPEN_ORIENTATION -> "open a task packet"
            BuddyActionType.CLAIM_GOAL -> "claim a goal"
            BuddyActionType.REQUEST_ATTESTATION -> "ask somebody to confirm your work"
            BuddyActionType.SUBMIT_VERIFICATION -> "submit an answer"
            // Unused in practice -- the gerund only appears in the no-project reason lines, which
            // this action never reaches. Present because the enum is exhaustive, which is what
            // made the compiler point at both of these when it was added.
            BuddyActionType.SET_GITHUB_LOGIN -> "save a GitHub username"
        }

    /** The result of proposing an action: what to tell the AI, and the proposal to show the hire (if any). */
    data class ProposeOutcome(
        val toolResult: String,
        val proposal: BuddyActionProposal?,
    )

    /** A proposed action for the hire to confirm — carried on the stream as an `action_proposal` event. */
    data class BuddyActionProposal(
        val action: String,
        val label: String,
        val question: String?,
        // Confirm payloads for the actions that carry one: the client echoes them back verbatim,
        // so the target of a confirmed action is the one the buddy proposed, never one the client
        // picked.
        val taskId: UUID? = null,
        val moduleId: UUID? = null,
        val answer: String? = null,
        val title: String? = null,
        val attesterId: String? = null,
        val githubLogin: String? = null,
    )

    private sealed interface ProjectResolution {
        data class Resolved(
            val projectId: UUID,
            val name: String,
        ) : ProjectResolution

        data object None : ProjectResolution

        data object Ambiguous : ProjectResolution
    }

    private sealed interface CallerContext {
        data class Resolved(
            val userId: UUID,
            val projectId: UUID,
            val projectName: String,
        ) : CallerContext

        data object NoProject : CallerContext

        data object MultipleProjects : CallerContext
    }

    private companion object {
        private fun noArgs() = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { })
        }

        val FLAG_TO_PM_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.FLAG_TO_PM.toolName,
            description = "Offer to escalate the hire's question to their project's PM, when neither the docs " +
                "nor the canonical answers cover it. This does NOT send anything — it shows the hire a confirm " +
                "button, and only they can send it. Provide the question to ask, phrased clearly, in `question`. " +
                "Use this as the last resort when you genuinely cannot ground an answer.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("question") {
                        put("type", "string")
                        put("description", "The question to send to the PM, phrased clearly for a person to answer.")
                    }
                }
                putJsonArray("required") { add("question") }
            },
        )

        val CLAIM_TASK_ZERO_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.CLAIM_TASK_ZERO.toolName,
            description = "Offer to start the hire's Task 0 — their first assigned starter task. This does NOT " +
                "assign anything by itself; it shows the hire a confirm button and runs only if they click. Use " +
                "when the hire is ready to begin their first piece of real work. Takes no arguments.",
            parameters = noArgs(),
        )

        val OPEN_ORIENTATION_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.OPEN_ORIENTATION.toolName,
            description = "Offer to assemble the task orientation packet for the hire's current task — a " +
                "step-by-step, cited guide to setting up, finding the code, making the change, and opening the " +
                "PR. Proposes only; the hire confirms. Use when they ask how to start the task they have. Takes " +
                "no arguments.",
            parameters = noArgs(),
        )

        val CLAIM_GOAL_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.CLAIM_GOAL.toolName,
            description = "Offer to claim a suggested starter-work task as the hire's goal — the " +
                "contribution their learning plan then aims at. This does NOT claim anything by " +
                "itself; it shows the hire a confirm button and runs only if they click. Use when " +
                "the hire picks one of the tasks get_suggested_tasks returned, passing that " +
                "task's task_id.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("task_id") {
                        put("type", "string")
                        put("description", "The task_id of the suggested task the hire picked.")
                    }
                }
                putJsonArray("required") { add("task_id") }
            },
        )

        val SET_GITHUB_LOGIN_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.SET_GITHUB_LOGIN.toolName,
            description = "Offer to save the hire's GitHub username. Use this the moment they tell " +
                "you one -- 'my GitHub is octocat', 'I'm @octocat on GitHub' -- rather than sending " +
                "them to Settings. It matters: this is the account their pull requests are matched " +
                "against, so until it is set, work they really do is not credited to them and " +
                "nothing says so. Pass exactly what they gave you, without the @. Proposing does " +
                "not save it; they confirm.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("login") {
                        put("type", "string")
                        put("description", "The GitHub username the hire gave, without a leading @.")
                    }
                }
                putJsonArray("required") { add("login") }
            },
        )

        val REQUEST_ATTESTATION_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.REQUEST_ATTESTATION.toolName,
            description = "Offer to ask a named colleague to confirm a piece of work the hire has " +
                "done — a facilitated ceremony, a published plan, anything no system can see. " +
                "This does NOT confirm anything by itself; it shows the hire a confirm button and " +
                "runs only if they click, and the colleague still has to agree. Use for work that " +
                "leaves no trace a tool can read. Read get_teammates first and ask the hire who " +
                "should confirm it — never guess, and never name the hire themselves.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "What the hire did, in their words.")
                    }
                    putJsonObject("attester_id") {
                        put("type", "string")
                        put("description", "The user id of the teammate the hire picked to confirm it.")
                    }
                }
                putJsonArray("required") {
                    add("title")
                    add("attester_id")
                }
            },
        )

        val SUBMIT_VERIFICATION_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.SUBMIT_VERIFICATION.toolName,
            description = "Offer to submit the hire's answer to a module's check for grading — a " +
                "pull request number for an artifact check, a statement for an attestation. This " +
                "does NOT submit anything by itself; it shows the hire a confirm button and runs " +
                "only if they click. Use when the hire has done the work a module's check asks " +
                "for. Grading and the ledger write happen through the ordinary attempt path — " +
                "you relay the verdict, you never judge the work yourself.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("module_id") {
                        put("type", "string")
                        put("description", "The id of the module whose check is being answered.")
                    }
                    putJsonObject("answer") {
                        put("type", "string")
                        put(
                            "description",
                            "The hire's answer: a pull request number for an artifact check, " +
                                "their statement for an attestation.",
                        )
                    }
                }
                putJsonArray("required") {
                    add("module_id")
                    add("answer")
                }
            },
        )
    }
}
