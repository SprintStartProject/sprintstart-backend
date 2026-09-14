package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.IngestedIssue
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.request.starterwork.PromoteStarterWorkCandidateRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/*
 * The actions of team mode's starter-work area.
 *
 * The pool has no project, and the services behind these actions load a task by id alone. So every
 * action resolves its target through StarterWorkScope — the task's repository must be linked to the
 * turn's project — when drafting and again when the manager confirms.
 *
 * A repository can be linked to several projects, and they all draw from the same rows. The previews
 * say so wherever that reaches past this project.
 */

/** The live pool task [taskId] names, if its repository is linked to [projectId]. */
private fun StarterWorkTaskProposalRepository.liveOn(
    taskId: UUID?,
    projectId: UUID,
    scope: StarterWorkScope,
): StarterWorkTaskProposal? =
    taskId
        ?.let { findById(it).orElse(null) }
        ?.takeIf { it.status == ProposalStatus.LIVE && scope.covers(it.sourceId, projectId) }

/** The refusal for a stored proposal whose task is no longer live on [TeamToolContext.projectId]. */
private fun StarterWorkTaskProposalRepository.goneSince(
    params: JsonObject,
    context: TeamToolContext,
    scope: StarterWorkScope,
): String? = TASK_GONE_SINCE.takeIf { liveOn(params.uuid("task_id"), context.projectId, scope) == null }

private fun StarterWorkTaskProposal.named(): String = "“$title” (${githubRepositoryOf(sourceId)})"

private const val NOT_IN_POOL_HERE =
    "That task is not in this project's starter-work pool. Call list_starter_work_pool for the tasks that are, " +
        "and pass the task_id it gives."

private const val TASK_GONE_SINCE =
    "That task left the starter-work pool since — it was removed, or its issue was closed — so nothing was changed."

/** How the ingestion mappers spell "finished at the source". */
private const val CLOSED_STATE = "CLOSED"

private fun taskIdOnly(description: String): JsonObject =
    stringFields("task_id" to description, required = listOf("task_id"))

/** Offers to record that somebody looked at a task and is happy to hand it to a newcomer. */
@Component
class MarkStarterWorkReviewedAction(
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val starterWorkTaskProposalService: StarterWorkTaskProposalService,
    private val starterWorkScope: StarterWorkScope,
) : TeamActionHandler {
    override val area = TeamArea.STARTER_WORK
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "mark_starter_work_reviewed",
        description = "Offer to mark a starter-work task reviewed: somebody looked at it and is happy to hand it " +
            "to a newcomer. It is already suggestable; reviewing stops it ranking lower. Read " +
            "list_unreviewed_starter_work first. This does NOT mark anything by itself; the manager confirms.",
        parameters = taskIdOnly("The task_id from list_unreviewed_starter_work."),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val task = starterWorkTaskProposalRepository.liveOn(
            call.uuidArgument("task_id"),
            context.projectId,
            starterWorkScope,
        ) ?: return TeamActionDraft.Refused(NOT_IN_POOL_HERE)
        if (task.reviewed) {
            return TeamActionDraft.Refused("That task is already reviewed, so there is nothing to confirm.")
        }
        return TeamActionDraft.Proposed(
            params = buildJsonObject { put("task_id", task.id.toString()) },
            label = "Mark reviewed: ${task.title.forLabel()}",
            preview = "Mark ${task.named()} reviewed.\n\nIt stays in the pool as it is; hires' suggestions stop " +
                "ranking it lower for being unreviewed.",
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        starterWorkTaskProposalRepository.goneSince(params, context, starterWorkScope)

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        starterWorkTaskProposalService.markReviewed(requireNotNull(params.uuid("task_id")))
        return "Marked reviewed. Hires' suggestions no longer rank it lower for being unreviewed."
    }
}

/** Offers to take a task out of the pool, permanently. */
@Component
class RejectStarterWorkAction(
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val starterWorkTaskProposalService: StarterWorkTaskProposalService,
    private val starterWorkScope: StarterWorkScope,
) : TeamActionHandler {
    override val area = TeamArea.STARTER_WORK
    override val risk = BuddyProposalRisk.DESTRUCTIVE
    override val spec = BuddyToolSpecDto(
        name = "reject_starter_work",
        description = "Offer to remove a starter-work task from the pool for good — for work that is wrong to " +
            "hand a newcomer. It is permanent: mining never proposes it again and it cannot be put back. Pass " +
            "the manager's reason when they gave one. This does NOT remove anything by itself; the manager confirms.",
        parameters = stringFields(
            "task_id" to "The task_id from list_starter_work_pool or list_unreviewed_starter_work.",
            "reason" to "Optional: why it is wrong for a newcomer, in the manager's words.",
            required = listOf("task_id"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val task = starterWorkTaskProposalRepository.liveOn(
            call.uuidArgument("task_id"),
            context.projectId,
            starterWorkScope,
        ) ?: return TeamActionDraft.Refused(NOT_IN_POOL_HERE)
        val reason = call.textArgument("reason")
        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("task_id", task.id.toString())
                put("reason", reason)
            },
            label = "Remove from pool: ${task.title.forLabel()}",
            preview = buildString {
                append("Remove ${task.named()} from the starter-work pool for good.\n\n")
                append("No hire is suggested it again, mining never proposes it again, and it cannot be put back ")
                append("from the open issues. Every project linked to ${githubRepositoryOf(task.sourceId)} ")
                append("loses it, not only this one.")
                if (reason.isNotBlank()) append("\n\nReason recorded: $reason")
            },
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        starterWorkTaskProposalRepository.goneSince(params, context, starterWorkScope)

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        starterWorkTaskProposalService.reject(
            requireNotNull(params.uuid("task_id")),
            params.text("reason").ifBlank { null },
        )
        return "Removed from the starter-work pool for good."
    }
}

/**
 * Offers to put an open issue into the pool as a reviewed task.
 *
 * The competency keys are left empty: nothing in the conversation tells the model which keys the
 * catalog uses, and empty is the honest value the promote endpoint already accepts.
 */
@Component
class PromoteCandidateAction(
    private val artifactIngestionApi: ArtifactIngestionApi,
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val starterWorkTaskProposalService: StarterWorkTaskProposalService,
    private val starterWorkScope: StarterWorkScope,
) : TeamActionHandler {
    override val area = TeamArea.STARTER_WORK
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "promote_candidate",
        description = "Offer to put an open issue from list_starter_work_candidates into the starter-work pool, " +
            "as a reviewed task hires can be suggested. Add a summary only when the manager gave one or the " +
            "issue makes plain why it suits a newcomer. This does NOT add anything by itself; the manager confirms.",
        parameters = stringFields(
            "source_id" to "The source_id from list_starter_work_candidates.",
            "summary" to "Optional: one sentence on why this suits a newcomer.",
            required = listOf("source_id"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val sourceId = call.textArgument("source_id")
        val issue = openIssueOn(sourceId, context.projectId)
            ?: return TeamActionDraft.Refused(
                "That is not an open issue from this project's repositories. Call list_starter_work_candidates " +
                    "and pass the source_id it gives.",
            )
        poolRefusal(sourceId)?.let { return TeamActionDraft.Refused(it) }
        val title = issue.title.orEmpty()
        val summary = call.textArgument("summary")
        val repository = githubRepositoryOf(sourceId)
        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("source_id", sourceId)
                put("summary", summary)
            },
            label = "Add to pool: ${title.forLabel()}",
            preview = buildString {
                append("Put the issue “$title” ($repository) into the starter-work pool as a reviewed task.\n\n")
                append("Hires on every project linked to $repository can be suggested it from now on.")
                if (summary.isNotBlank()) append("\n\nSummary shown with it: $summary")
                issue.sourceUrl?.let { append("\n\n$it") }
            },
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val sourceId = params.text("source_id")
        if (openIssueOn(sourceId, context.projectId) == null) {
            return "That issue was closed, or its repository is no longer linked to this project, so nothing was added."
        }
        return poolRefusal(sourceId)?.let { "$it Nothing was added." }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val added = try {
            starterWorkTaskProposalService.promoteCandidate(
                PromoteStarterWorkCandidateRequest(
                    sourceId = params.text("source_id"),
                    summary = params.text("summary").ifBlank { null },
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            // Two promotions of the same issue at once: the unique source id lets only one row in.
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Somebody put that issue in the pool at the same moment, so it was not added twice.",
            )
        }
        return "Added “${added.title}” to the starter-work pool as a reviewed task."
    }

    private fun openIssueOn(sourceId: String, projectId: UUID): IngestedIssue? {
        if (sourceId.isBlank() || !starterWorkScope.covers(sourceId, projectId)) return null
        return artifactIngestionApi
            .getIssue(sourceId)
            ?.takeIf { !CLOSED_STATE.equals(it.state, ignoreCase = true) && !it.title.isNullOrBlank() }
    }

    /** Why the issue cannot join the pool, or null when it can — a stale row is revived, not refused. */
    private fun poolRefusal(sourceId: String): String? =
        when (starterWorkTaskProposalRepository.findBySourceId(sourceId)?.status) {
            ProposalStatus.LIVE -> "That issue is already in the starter-work pool."
            ProposalStatus.REJECTED ->
                "That issue was removed from the starter-work pool for good and cannot be put back."
            ProposalStatus.STALE, null -> null
        }
}

/**
 * Offers to flag a task as a Task 0 candidate, or to take the flag off.
 *
 * Task 0 is picked from every flagged task, whichever project the hire is on — the preview says so,
 * because that reaches further than the repositories this manager's project is linked to.
 */
@Component
class SetTaskZeroEligibleAction(
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val taskZeroService: TaskZeroService,
    private val starterWorkScope: StarterWorkScope,
) : TeamActionHandler {
    override val area = TeamArea.STARTER_WORK
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "set_task_zero_eligible",
        description = "Offer to flag a starter-work task as a Task 0 candidate — the small first task a new hire " +
            "is given automatically to walk branch, pull request, review and merge once — or to take the flag " +
            "off. Only something trivial suits Task 0. This does NOT change anything by itself; the manager confirms.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("task_id") {
                    put("type", "string")
                    put("description", "The task_id from list_starter_work_pool.")
                }
                putJsonObject("eligible") {
                    put("type", "boolean")
                    put("description", "true to flag it for Task 0, false to take the flag off.")
                }
            }
            putJsonArray("required") {
                add("task_id")
                add("eligible")
            }
        },
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val task = starterWorkTaskProposalRepository.liveOn(
            call.uuidArgument("task_id"),
            context.projectId,
            starterWorkScope,
        ) ?: return TeamActionDraft.Refused(NOT_IN_POOL_HERE)
        val eligible = call.booleanArgument("eligible")
            ?: return TeamActionDraft.Refused(
                "Say whether to flag it for Task 0 (eligible: true) or take the flag off (eligible: false).",
            )
        if (task.taskZeroEligible == eligible) {
            val state = if (eligible) "already a Task 0 candidate" else "not a Task 0 candidate"
            return TeamActionDraft.Refused("Nothing would change: that task is $state.")
        }
        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("task_id", task.id.toString())
                put("eligible", eligible)
            },
            label = (if (eligible) "Flag for Task 0: " else "Unflag Task 0: ") + task.title.forLabel(),
            preview = if (eligible) {
                "Flag ${task.named()} as a Task 0 candidate.\n\nA new hire without a Task 0 can be assigned it " +
                    "automatically as their first task. Task 0 is picked from every flagged task, so that hire " +
                    "may be on any project, not only this one."
            } else {
                "Take the Task 0 flag off ${task.named()}.\n\nNo new hire is assigned it from now on; a hire who " +
                    "already has it as their Task 0 keeps it."
            },
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        starterWorkTaskProposalRepository.goneSince(params, context, starterWorkScope)

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val eligible = requireNotNull(params.boolean("eligible"))
        taskZeroService.setEligibility(requireNotNull(params.uuid("task_id")), eligible)
        return if (eligible) "Flagged as a Task 0 candidate." else "The Task 0 flag is off."
    }
}

/**
 * Offers to bring the pool in line with its trackers now.
 *
 * The reconciler has no project either and checks every row. It only ever writes what the ingested
 * trackers already say, and never touches a removed task, so a manager running it early changes
 * nothing that the next scheduled pass would not — the preview says it covers the whole pool.
 */
@Component
class ReconcileStarterWorkAction(
    private val starterWorkPoolReconciler: StarterWorkPoolReconciler,
) : TeamActionHandler {
    override val area = TeamArea.STARTER_WORK
    override val risk = BuddyProposalRisk.BULK
    override val spec = BuddyToolSpecDto(
        name = "reconcile_starter_work",
        description = "Offer to check the starter-work pool against the trackers right now: tasks whose issue " +
            "closed leave the pool, reopened ones come back. It also runs on a schedule, so offer it only when " +
            "the manager wants the pool current immediately. Takes no arguments.",
        parameters = stringFields(required = emptyList()),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft =
        TeamActionDraft.Proposed(
            params = JsonObject(emptyMap()),
            label = "Check the starter-work pool now",
            preview = "Check every task in the starter-work pool against the issues as last ingested: tasks whose " +
                "issue was closed leave the pool, and ones whose issue reopened come back. Removed tasks are never " +
                "touched.\n\nThis covers the whole pool, not only this project's tasks, and it runs on a schedule " +
                "anyway.",
        )

    override fun recheck(params: JsonObject, context: TeamToolContext): String? = null

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val outcome = starterWorkPoolReconciler.reconcile()
        return buildString {
            append("Checked ${outcome.examined} tasks across the whole pool: ${outcome.markedStale} left because ")
            append("their issue closed, ${outcome.revived} came back because it reopened.")
            if (outcome.skipped > 0) {
                append(" ${outcome.skipped} could not be checked because their issue is no longer ingested.")
            }
        }
    }
}
