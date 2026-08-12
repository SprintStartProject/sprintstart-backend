package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.AuthoredPullRequest
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.HireTimelineResponse
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Runs the backend-owned tools the buddy agent may call, and describes them to the AI reasoner.
 *
 * The buddy answers corpus questions AI-side (``search_docs``); tools here answer questions about
 * the hire's *own* onboarding, which only the backend can see. Each tool is executed strictly on
 * behalf of the resolved caller — the agent never supplies whose data to read, so one hire can
 * never read another's metrics through the buddy.
 *
 * One function per buddy tool (plus the shared state snapshot the opener grounds itself in); the
 * count tracks how much the buddy can read about the hire, not a class doing unrelated things,
 * hence the suppression.
 */
@Suppress("TooManyFunctions")
@Component
class BuddyToolExecutor(
    private val onboardingMetricsService: OnboardingMetricsService,
    private val myCompetencyService: MyCompetencyService,
    private val starterWorkTaskProposalService: StarterWorkTaskProposalService,
    private val knowledgeBaseService: KnowledgeBaseService,
    private val userApi: UserApi,
    private val buddyPlanTools: BuddyPlanTools,
    private val buddyBoardTools: BuddyBoardTools,
    private val openPullRequestReader: OpenPullRequestReader,
    private val trackService: TrackService,
    private val projectMembershipApi: ProjectMembershipApi,
    private val arrivalStepService: ArrivalStepService,
) {
    /**
     * The backend tools the AI reasoner is told it may call, for this hire.
     *
     * ⚠️ Mounted per hire, not globally: a tool the hire's role can never have evidence for
     * invites the mentor to raise something they will never have. The gate is the track's evidence
     * kinds, so a new evidence source mounts its own tools by declaring itself rather than by
     * editing this list.
     */
    fun toolSpecs(userId: UUID): List<BuddyToolSpecDto> = buildList {
        // ⚠️ First: what has to be true before somebody can work comes before how their work is
        // going. Mounted only when a step applies -- "absent, never empty", read from the same
        // service the board card uses so the two cannot disagree.
        if (arrivalStepService.forHire(userId).isNotEmpty()) {
            add(GET_ARRIVAL_STEPS_SPEC)
        }
        add(GET_MY_METRICS_SPEC)
        add(GET_MY_COMPETENCIES_SPEC)
        // Position matters: the spec order is the order the reasoner sees.
        if (admitsPullRequests(userId)) {
            add(GET_MY_OPEN_PULL_REQUESTS_SPEC)
        }
        add(GET_SUGGESTED_TASKS_SPEC)
        add(SEARCH_CANONICAL_ANSWERS_SPEC)
        // Gated on attestation: naming teammates is only useful when the hire can ask one of
        // them to confirm something.
        if (trackService.admitsAnywhere(userId, ContributionEvidenceKind.ATTESTATION)) {
            add(GET_TEAMMATES_SPEC)
        }
        addAll(buddyPlanTools.toolSpecs())
        addAll(buddyBoardTools.toolSpecs())
    }

    private fun admitsPullRequests(userId: UUID): Boolean =
        trackService.admitsAnywhere(userId, ContributionEvidenceKind.PULL_REQUEST)

    /**
     * A plain-text snapshot of the hire's own onboarding, for the buddy's opening greeting to
     * ground itself in. Reuses the exact reads the caller-scoped tools expose, so the opener and
     * the tools can never describe different states.
     *
     * ⚠️ **Arrival comes first, and the order is the feature** — a greeting grounded in progress
     * before setup greets a hire who cannot clone the repository with a good first issue. Omitted
     * entirely when no step applies: a greeting grounded in "arrival: nothing" will find something
     * to say about it.
     */
    fun stateSnapshot(userId: UUID): String =
        listOfNotNull(
            ("Before they can work:\n" + getArrivalSteps(userId))
                .takeIf { arrivalStepService.forHire(userId).isNotEmpty() },
            "Progress:\n" + getMyMetrics(userId),
            // ⚠️ Omitted entirely for a track that cannot have pull requests, never included as
            // an empty section -- a greeting grounded in "Open pull requests: none" will raise
            // them anyway.
            ("Open pull requests:\n" + getMyOpenPullRequests(userId)).takeIf { admitsPullRequests(userId) },
            "Suggested tasks:\n" + getSuggestedTasks(userId),
            "Competencies:\n" + getMyCompetencies(userId),
        ).joinToString("\n\n")

    /** Executes [call] on behalf of [userId], returning a plain-text result for the model. */
    fun execute(call: BuddyToolCallDto, userId: UUID): String =
        when {
            buddyPlanTools.handles(call.name) -> buddyPlanTools.execute(call, userId)
            buddyBoardTools.handles(call.name) -> buddyBoardTools.execute(call, userId)
            else -> when (call.name) {
                GET_ARRIVAL_STEPS -> getArrivalSteps(userId)
                GET_MY_METRICS -> getMyMetrics(userId)
                GET_MY_COMPETENCIES -> getMyCompetencies(userId)
                GET_MY_OPEN_PULL_REQUESTS -> getMyOpenPullRequests(userId)
                GET_SUGGESTED_TASKS -> getSuggestedTasks(userId)
                SEARCH_CANONICAL_ANSWERS -> searchCanonicalAnswers(userId, call.stringArg("query"))
                GET_TEAMMATES -> getTeammates(userId)
                else -> "Unknown tool: ${call.name}."
            }
        }

    /**
     * Who else is on the hire's projects, by id and name.
     *
     * ⚠️ The hire themselves is excluded from the list: an attestation confirmed by the person
     * who did the work is not evidence, and never showing the option is what stops the buddy
     * proposing it.
     */
    private fun getTeammates(userId: UUID): String {
        val projects = userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()
        if (projects.isEmpty()) {
            return "You are not a member of any project yet, so there are no teammates to name."
        }
        val sections = projects.map { project ->
            val others = projectMembershipApi
                .getProjectMembers(project.projectId)
                .filter { it.userId != userId }
            val lines = others
                .map { "- ${it.displayName} (id: ${it.userId})" }
                .ifEmpty { listOf("- nobody else is on this project yet") }
            (listOf("Project: ${project.name}") + lines).joinToString("\n")
        }
        return sections.joinToString("\n\n")
    }

    /**
     * What has to be true before this hire can work, and what they have settled.
     *
     * The part a checklist cannot do is the reason this is a tool and not just a card: a hire can
     * ask *why* a step exists, say they are stuck on one, and be told who to chase — none of which
     * a row with a tick box answers.
     *
     * ### Two things this text refuses to say
     *
     * ⚠️ **No total and no fraction.** "2 of 5 done" over a mix of what the system observed and
     * what the hire simply told us is meaningless, and a model handed such a number will repeat
     * it. Outstanding and settled are listed, and settled
     * steps say *how* — never summed together. `BuddyToolExecutorTest` asserts no blended figure.
     *
     * **Nothing is described as blocking.** An outstanding step never stopped anybody claiming work
     * or asking a question; `ArrivalStepService` has no method that could answer "may they
     * proceed". Saying otherwise here would reintroduce the gate in the one place nobody would
     * think to look for it — the model's own words.
     */
    private fun getArrivalSteps(userId: UUID): String {
        val steps = arrivalStepService.forHire(userId)
        if (steps.isEmpty()) {
            return "Nobody has written an arrival list for this hire, so there is nothing outstanding."
        }

        val (settled, outstanding) = steps.partition { it.settled }

        return buildString {
            if (outstanding.isEmpty()) {
                appendLine("Nothing is outstanding — every arrival step is settled.")
            } else {
                appendLine("Still outstanding (none of this stops them working):")
                outstanding.forEach { resolved ->
                    append("- ${resolved.step.title}")
                    // Which project's step this is, for the same reason the card needs a heading:
                    // a hire on two projects can be told "get staging access" twice, and a mentor
                    // that cannot say which one is asking them to guess.
                    resolved.projectName?.let { append(" (on $it)") }
                    resolved.step.description?.let { append(" — $it") }
                    resolved.step.href?.let { append(" ($it)") }
                    // Whether the hire can settle it themselves decides what the buddy may offer.
                    // Offering to tick a step the backend refuses would end in an error the hire
                    // never caused.
                    if (!resolved.step.selfConfirmable) {
                        append(" [we check this one ourselves; they cannot mark it done]")
                    }
                    appendLine()
                }
            }

            if (settled.isNotEmpty()) {
                appendLine()
                appendLine("Already settled:")
                settled.forEach { resolved ->
                    val how = if (resolved.rigor == Rigor.OBSERVED) {
                        "we confirmed this"
                    } else {
                        "they told us"
                    }
                    appendLine("- ${resolved.step.title} ($how)")
                }
            }
        }.trim()
    }

    private fun getMyMetrics(userId: UUID): String {
        val projects = userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()
        if (projects.isEmpty()) {
            return "You are not a member of any project yet, so there are no onboarding metrics."
        }
        val described = projects.mapNotNull { project ->
            onboardingMetricsService
                .getHireTimeline(userId, project.projectId)
                ?.let { describe(project.name, it) }
        }
        return described
            .ifEmpty { listOf("No onboarding metrics are available for you yet.") }
            .joinToString("\n\n")
    }

    private fun describe(projectName: String, timeline: HireTimelineResponse): String = buildString {
        appendLine("Project: $projectName")
        appendLine("- Open pull requests: ${timeline.openContributionCount}")
        appendLine("- Merged pull requests: ${timeline.acceptedContributionCount}")
        timeline.longestOpenWaitHours?.let {
            appendLine("- Longest pull request currently waiting on a review: $it hours")
        }
        timeline.hoursToFirstResponse?.let {
            appendLine("- Time from your first pull request to its first response: $it hours")
        }
        val stall = if (timeline.stalled) {
            "yes" + (timeline.stalledReason?.let { " ($it)" } ?: "")
        } else {
            "no"
        }
        appendLine("- Stalled: $stall")
        appendLine("- Pull requests sent back for changes: ${timeline.returnedContributionCount}")
        timeline.autonomyReachedAt?.let { appendLine("- Reached autonomy at: $it") }
    }.trim()

    /**
     * The hire's still-open pull requests, named — so the buddy can say *which* pull request is
     * stuck, not just how many. [getMyMetrics] reports the count and the longest wait; this fills
     * the gap it leaves: the numbers, titles and links.
     *
     * "Open" means genuinely open — [AuthoredPullRequest.isOpen], not merely unmerged — so a pull
     * request closed without merging is excluded, and the list matches the "open pull requests"
     * count [getMyMetrics] reports.
     */
    private fun getMyOpenPullRequests(userId: UUID): String {
        val login = userApi.getGithubLoginByUserId(userId)
        if (login.isNullOrBlank()) {
            return "You haven't set a GitHub username yet, so I can't look up your pull requests. " +
                "Add it on your profile page and I'll be able to list them."
        }
        val projects = userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()
        if (projects.isEmpty()) {
            return "You are not a member of any project yet, so there are no pull requests to show."
        }
        val sections = projects.mapNotNull { project ->
            // ⚠️ Which pull requests count as open, and which leads, are decided in one place --
            // the board shows the same list as cards and the two must not disagree.
            val open = openPullRequestReader.openFor(project.projectId, login)
            if (open.isEmpty()) {
                null
            } else {
                buildString {
                    appendLine("On ${project.name}, your open pull requests:")
                    open.forEach { pr -> appendPullRequest(pr) }
                }.trim()
            }
        }
        return sections
            .ifEmpty { listOf("You have no open pull requests right now.") }
            .joinToString("\n\n")
    }

    private fun StringBuilder.appendPullRequest(pr: AuthoredPullRequest) {
        val id = pr.number?.let { "#$it" } ?: "(number unknown)"
        appendLine("- $id ${pr.title ?: "(untitled)"}")
        openPullRequestReader.waitingHours(pr)?.let {
            appendLine("    · waiting $it hours for a first review")
        }
        pr.sourceUrl?.let { appendLine("    ($it)") }
    }

    private fun getMyCompetencies(userId: UUID): String {
        // Level-0 rows are placed-but-unknown, not evidence of a skill — exclude them, matching how
        // the skills rail treats them, so the buddy never reports a competency the hire hasn't shown.
        val ledger = myCompetencyService.getCompetenciesForUser(userId).filter { it.level > 0 }
        if (ledger.isEmpty()) {
            return "You have no demonstrated competencies on your ledger yet — that's normal early on."
        }
        val (held, inProgress) = ledger.partition { it.level >= it.targetLevel }
        return buildString {
            appendLine("Competencies held (meet their target level): ${held.size}")
            held.forEach { appendLine("- ${it.label} (level ${it.level}/${it.targetLevel})") }
            if (inProgress.isNotEmpty()) {
                appendLine("Below target (progress made, not yet met): ${inProgress.size}")
                inProgress.forEach { appendLine("- ${it.label} (level ${it.level}/${it.targetLevel})") }
            }
        }.trim()
    }

    private fun getSuggestedTasks(userId: UUID): String {
        val projects = userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()
        if (projects.isEmpty()) {
            return "You are not a member of any project yet, so there are no suggested tasks."
        }
        val sections = projects.mapNotNull { project ->
            val ranked = starterWorkTaskProposalService
                .matchForUserId(userId, project.projectId)
                .take(MAX_SUGGESTED_TASKS)
            if (ranked.isEmpty()) {
                null
            } else {
                buildString {
                    appendLine("On ${project.name}, good next tasks (best first):")
                    ranked.forEach { match ->
                        // The id is how the hire claims one: the claim_goal action names the task
                        // by it, so the suggestion and the claim can never drift onto different
                        // tasks.
                        appendLine("- ${match.task.title} [task_id: ${match.task.id}]")
                        // Show the reasons, never the score: a number is not a reason a hire can act
                        // on, and the ranker exists to explain itself.
                        match.reasons.forEach { appendLine("    · $it") }
                        match.task.sourceUrl?.let { appendLine("    ($it)") }
                    }
                }.trim()
            }
        }
        return sections
            .ifEmpty {
                listOf("There are no starter-work tasks to suggest yet.")
            }.joinToString("\n\n")
    }

    private fun searchCanonicalAnswers(userId: UUID, query: String): String {
        if (query.isBlank()) return "No search query was provided."
        val matches = knowledgeBaseService.searchForUser(userId, query)
        if (matches.isEmpty()) {
            return "No teammate has answered anything like this yet."
        }
        return matches.joinToString("\n\n") { "Q: ${it.question}\nA: ${it.answer}" }
    }

    /** Reads a string argument the model passed to a tool, or "" when it is missing/non-text. */
    private fun BuddyToolCallDto.stringArg(name: String): String =
        (arguments[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

    /**
     * ⚠️ Not private: [BuddySuggestionService] binds its chips to these constants, so renaming a
     * tool stops the chip catalog compiling rather than quietly offering a hire something the
     * mentor cannot answer.
     */
    companion object {
        const val GET_ARRIVAL_STEPS = "get_arrival_steps"
        const val GET_MY_METRICS = "get_my_metrics"
        const val GET_MY_COMPETENCIES = "get_my_competencies"
        const val GET_MY_OPEN_PULL_REQUESTS = "get_my_open_pull_requests"
        const val GET_SUGGESTED_TASKS = "get_suggested_tasks"
        const val SEARCH_CANONICAL_ANSWERS = "search_canonical_answers"
        const val GET_TEAMMATES = "get_teammates"
        const val MAX_SUGGESTED_TASKS = 3

        // No-argument JSON schema shared by every caller-scoped tool: the agent never says whose
        // data to read, so there is nothing to pass.
        private fun noArgs() = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { })
        }

        val GET_ARRIVAL_STEPS_SPEC = BuddyToolSpecDto(
            name = GET_ARRIVAL_STEPS,
            description = "What has to be true before this hire can work — accounts, access, a " +
                "working setup — and which of it they have settled. Read this before " +
                "suggesting a task: somebody still waiting on an access grant does not need a " +
                "good first task, they need the access. Use it for 'what do I still need to do?', " +
                "'how do I get set up?', or whenever they say they are stuck getting started. " +
                "**None of it blocks them** — an outstanding step is a thing to chase, never a " +
                "reason they may not claim work or ask you something. Never total the steps up or " +
                "give a fraction: what the system confirmed and what they told us are different " +
                "facts. Takes no arguments — it always reads the caller.",
            parameters = noArgs(),
        )

        val GET_MY_METRICS_SPEC = BuddyToolSpecDto(
            name = GET_MY_METRICS,
            description = "The hire's own onboarding metrics on the project(s) they are onboarding " +
                "on: open and merged pull requests, how long a pull request has been waiting on a " +
                "review, whether they are stalled, review rework, and whether they have reached " +
                "autonomy. Use this for questions about the hire's own progress, e.g. 'is my PR " +
                "stuck?' or 'am I on track?'. Takes no arguments — it always reads the caller.",
            parameters = noArgs(),
        )

        val GET_MY_COMPETENCIES_SPEC = BuddyToolSpecDto(
            name = GET_MY_COMPETENCIES,
            description = "The hire's own competency ledger: which skills they have demonstrated " +
                "(and at what level vs the target), and which they have made progress toward but " +
                "not yet met. Use this for questions about where the hire stands or what they have " +
                "shown, e.g. 'where do I stand?' or 'what have I proven so far?'. Takes no " +
                "arguments — it always reads the caller.",
            parameters = noArgs(),
        )

        val GET_MY_OPEN_PULL_REQUESTS_SPEC = BuddyToolSpecDto(
            name = GET_MY_OPEN_PULL_REQUESTS,
            description = "The hire's own still-open pull requests (not merged and not closed), " +
                "each with its number, title, link, and how long it has been waiting for a first " +
                "review. Use " +
                "this whenever the hire asks which pull requests they have open, or to name the " +
                "specific pull request that is stuck — `get_my_metrics` only gives the count and " +
                "the longest wait, not the identifiers. Takes no arguments — it always reads the " +
                "caller.",
            parameters = noArgs(),
        )

        val GET_SUGGESTED_TASKS_SPEC = BuddyToolSpecDto(
            name = GET_SUGGESTED_TASKS,
            description = "Good next starter-work tasks for the hire, ranked by fit, each with the " +
                "plain reasons it was suggested and the task_id to claim it by. Use this for " +
                "questions like 'what should I work on?' or 'what's a good first task for me?'. " +
                "Present the reasons, never a score. When the hire picks one, offer to claim it " +
                "as their goal with claim_goal. Takes no arguments — it always ranks for the caller.",
            parameters = noArgs(),
        )

        val GET_TEAMMATES_SPEC = BuddyToolSpecDto(
            name = GET_TEAMMATES,
            description = "The other people on the hire's projects, with their ids. Read this " +
                "before offering request_attestation, so the hire can pick who should confirm " +
                "their work and you pass that person's real id. The hire is never in this list — " +
                "work confirmed by the person who did it is not evidence.",
            parameters = noArgs(),
        )

        val SEARCH_CANONICAL_ANSWERS_SPEC = BuddyToolSpecDto(
            name = SEARCH_CANONICAL_ANSWERS,
            description = "Durable answers a teammate previously gave to questions the buddy could " +
                "not answer from the docs — how-we-do-things, conventions, tribal knowledge. These " +
                "are authoritative and human-written: prefer them, and quote them faithfully. " +
                "Search here alongside the docs for any 'how do we…' or 'what's our…' question. " +
                "If neither the docs nor these cover it, say so plainly so the hire can escalate.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "What the hire is asking, as a search query.")
                    }
                }
                putJsonArray("required") { add("query") }
            },
        )
    }
}
