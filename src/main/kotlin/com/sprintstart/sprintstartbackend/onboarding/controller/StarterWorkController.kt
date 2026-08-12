package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.onboarding.model.request.competency.RejectProposalRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.starterwork.ClaimGoalRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.starterwork.CreateStarterWorkTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.starterwork.PromoteStarterWorkCandidateRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.goal.GoalView
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.GenerateStarterWorkResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.RankedStarterWorkTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.StarterWorkCandidateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.StarterWorkTaskProposalResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.UnreviewedStarterWorkResponse
import com.sprintstart.sprintstartbackend.onboarding.service.StarterWorkTaskProposalService
import com.sprintstart.sprintstartbackend.onboarding.service.UserGoalService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.flow.Flow
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Exposes the AI-mined starter-work pool (contribution-node sourcing): PM review of mined tasks,
 * and hire-facing fit ranking of the approved pool.
 */
@RestController
@RequestMapping("/api/v1/onboarding/starter-work")
@Tag(
    name = "Onboarding - Starter Work",
    description = "Review AI-mined starter-work task proposals and rank them by hire fit",
)
// One method per endpoint; the pool is reached three ways (mined, hand-written, picked from the
// corpus) and read by two audiences, which is what the count reflects.
@Suppress("TooManyFunctions")
class StarterWorkController(
    private val starterWorkTaskProposalService: StarterWorkTaskProposalService,
    private val userGoalService: UserGoalService,
) {
//  ========================== Endpoints for admins ==========================

    /**
     * Triggers AI starter-work mining over the ingested corpus's open tracker issues.
     *
     * Mined tasks are stored `LIVE`, one row per issue, and are claimable immediately. ⚠️ Nothing
     * waits on a person here; reviewing one only lifts the fit-ranking demotion it carries.
     */
    @Operation(
        summary = "Mine starter-work task proposals",
        description = "Triggers AI starter-work mining over the ingested corpus's open tracker issues",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Mining outcome returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    suspend fun generate(): GenerateStarterWorkResponse = starterWorkTaskProposalService.generate()

    /**
     * The streaming twin of [generate]: watch the starter-work pool fill one task at a time.
     *
     * Same mining and same stored pool as `POST /generate`, streamed as Server-Sent Events so a PM
     * sees each task land as it clears the scope-safety judgement, with a terminal `done` once the
     * proposals are stored. The stream is a view — a client reloads the proposal queue on `done`.
     */
    @Operation(
        summary = "Watch starter-work tasks being mined (streaming)",
        description = "The same mining as `POST /generate`, streamed as Server-Sent Events so a PM can " +
            "watch the pool fill: a `stage` per pass, an `item` per task as it clears scope safety, and " +
            "a terminal `done` once the proposals are stored. The stored pool is identical to the " +
            "non-streaming path.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Progress stream started"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/generate/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    suspend fun streamGenerate(): Flow<AiProgressEvent> = starterWorkTaskProposalService.streamGenerate()

    /**
     * Creates a hand-authored starter-work task, with no AI mining in the loop.
     *
     * The origination counterpart to mining: mining fills the pool from ingested issues, this adds
     * a task the corpus never surfaced. Born reviewed and claimable immediately -- writing a task
     * is vouching for it, so it never joins the unreviewed list.
     */
    @Operation(
        summary = "Create a starter-work task by hand",
        description = "Hand-authors a live starter-work task, with no AI mining — AI help is optional",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Task created and its contribution node added"),
            ApiResponse(responseCode = "400", description = "Blank title"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun create(
        @RequestBody request: CreateStarterWorkTaskRequest,
    ): StarterWorkTaskProposalResponse = starterWorkTaskProposalService.createTask(request)

    /**
     * Lists the open issues a project's corpus already holds, for somebody picking starter work.
     *
     * ⚠️ **Not a queue and not a shortlist.** Nothing here has been judged and nothing is waiting on
     * a decision — these are simply the project's open issues, shown so a person can put one in the
     * pool themselves. Mining keeps filling the pool live regardless.
     *
     * Costs a query and no model call: the candidate filter is deterministic.
     */
    @Operation(
        summary = "Browse a project's open corpus issues",
        description = "Lists the open tracker issues already ingested for a project, each marked with " +
            "whether it is already in the starter-work pool or was removed from it. Nothing is ranked " +
            "or filtered — issues somebody else is assigned are included and marked, because an issue " +
            "missing from the list gives a reader no way to tell 'taken' from 'not ingested'. No AI.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Candidate issues returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun listCandidates(
        @Parameter(description = "Project whose ingested issues to browse")
        @RequestParam projectId: UUID,
    ): List<StarterWorkCandidateResponse> = starterWorkTaskProposalService.listCandidates(projectId)

    /**
     * Puts one browsed issue into the starter-work pool, live and reviewed.
     *
     * The hand-authored path with a source id instead of typed text — so it lands exactly where a
     * task written in the blank form lands. ⚠️ **This adds a way in; it gates nothing.**
     */
    @Operation(
        summary = "Put a browsed issue into the starter-work pool",
        description = "Promotes one ingested open issue into the pool. It lands live and reviewed, the " +
            "same as a hand-authored task, because somebody looked at it — this is a second way to add " +
            "work, not an approval step in front of mining. An issue already pooled or previously " +
            "removed is refused rather than duplicated or quietly revived.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Issue added to the pool"),
            ApiResponse(responseCode = "400", description = "The ingested issue has no title"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No ingested issue with that source id"),
            ApiResponse(
                responseCode = "409",
                description = "The issue is already in the pool, was removed from it, or is closed at its source",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/candidates/promote")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun promoteCandidate(
        @RequestBody request: PromoteStarterWorkCandidateRequest,
    ): StarterWorkTaskProposalResponse = starterWorkTaskProposalService.promoteCandidate(request)

    /**
     * Lists the live starter-work tasks nobody has vouched for yet.
     *
     * ⚠️ **Not a queue anything waits in.** These tasks are already claimable — mining lands them
     * live — so this is the set a PM has not looked at, not the set held back from
     * hires.
     */
    @Operation(
        summary = "List starter-work tasks nobody has reviewed yet",
        description = "Returns live starter-work tasks no PM has vouched for. They are already claimable — " +
            "reviewing one lifts the fit-ranking demotion it carries, it does not admit it to the pool.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Unreviewed tasks returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/unreviewed")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun listUnreviewed(): UnreviewedStarterWorkResponse = starterWorkTaskProposalService.listUnreviewed()

    /**
     * Lists the whole live starter-work pool, for a PM choosing one to author orientation for.
     */
    @Operation(
        summary = "List the live starter-work pool",
        description = "Returns every live starter-work task — reviewed or not — which is the pool a PM can " +
            "author task orientation for and the pool hires are ranked against.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Pool returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/pool")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun listPool(): List<StarterWorkTaskProposalResponse> = starterWorkTaskProposalService.listPool()

    /**
     * Records that somebody has looked at a starter-work task and is happy with it.
     *
     * ⚠️ **This admits nothing.** It was claimable before and it is claimable after; what changes
     * is that `StarterWorkMatcher` stops demoting it for being unvouched. ⚠️ It is deliberately
     * not called "approve" -- that would name a gate this pool does not have.
     */
    @Operation(
        summary = "Mark a starter-work task reviewed",
        description = "Records that a PM has looked at the task and is happy with it. It was already " +
            "claimable — this lifts the fit-ranking demotion an unreviewed task carries. Idempotent.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Task marked reviewed"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No task found with the given id"),
            ApiResponse(responseCode = "409", description = "The task was removed from the pool"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun markReviewed(
        @Parameter(description = "UUID of the starter-work task to mark reviewed")
        @PathVariable id: UUID,
    ): StarterWorkTaskProposalResponse = starterWorkTaskProposalService.markReviewed(id)

    /**
     * Takes a starter-work task out of the pool for good.
     *
     * The one irreversible action here, and **not the opposite of reviewing**: reviewing changes a
     * ranking, this removes work from the pool. Sticky, so mining never brings it back.
     */
    @Operation(
        summary = "Remove a starter-work task from the pool",
        description = "Takes the task out of the pool for good. Sticky: mining never re-proposes a task " +
            "somebody removed, or they would remove it again after every crawl.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Task removed"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No task found with the given id"),
            ApiResponse(responseCode = "409", description = "The task was already removed from the pool"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun reject(
        @Parameter(description = "UUID of the starter-work task to remove")
        @PathVariable id: UUID,
        @RequestBody(required = false) request: RejectProposalRequest?,
    ): StarterWorkTaskProposalResponse = starterWorkTaskProposalService.reject(id, request?.reason)

//  ========================== Endpoints for users (/me/...) ==========================

    /**
     * Ranks the live starter-work pool by fit against the authenticated user's competencies.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     */
    @Operation(
        summary = "Get current user's starter-work matches",
        description = "Ranks the live starter-work pool by fit for the authenticated user on a " +
            "project. Deterministic and local — no AI call: competency overlap is one input among " +
            "task type, prior involvement and label familiarity, and a repository that answers " +
            "pull requests slowly demotes its tasks without ever hiding them. Every task carries " +
            "the reasons it was suggested, because a suggestion nobody can interrogate is an " +
            "instruction.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Ranked matches returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated principal"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/matches")
    @PreAuthorize("hasRole('USER')")
    fun getMatchesForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ): List<RankedStarterWorkTaskResponse> = starterWorkTaskProposalService.matchForUser(jwt.subject, projectId)

    /**
     * Claims a live starter-work task as the authenticated hire's goal for a project.
     *
     * The hire chooses their own destination from the ranked matches above; a PM still controls
     * which tasks exist by approving proposals, so this is a choice within a curated set.
     */
    @Operation(
        summary = "Claim a starter-work task as my goal",
        description = "Sets the contribution the authenticated user's path for this project aims at, " +
            "replacing any goal they had claimed there before",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Goal claimed"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(
                responseCode = "404",
                description = "No such task, no user for the principal, or the task has no contribution node",
            ),
            ApiResponse(responseCode = "409", description = "The task has not been approved"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/me/goal")
    @PreAuthorize("hasRole('USER')")
    fun claimGoalForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
        @RequestBody request: ClaimGoalRequest,
    ): GoalView = userGoalService.claimForMe(jwt.subject, projectId, request.taskId)

    /**
     * Drops the authenticated hire's goal for a project; their path falls back to the baseline.
     */
    @Operation(
        summary = "Drop my goal",
        description = "Clears the contribution this user's path for the given project aims at",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Goal cleared"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated principal"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/goal")
    @PreAuthorize("hasRole('USER')")
    fun clearGoalForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ) = userGoalService.clearForMe(jwt.subject, projectId)
}
