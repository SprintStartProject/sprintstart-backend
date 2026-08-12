package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.AuthoredPullRequest
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.external.enums.TaskType
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CanonicalAnswer
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.MyCompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.HireTimelineResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.HireVocabularyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.RankedStarterWorkTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.StarterWorkTaskProposalResponse
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class BuddyToolExecutorTest {
    private val onboardingMetricsService: OnboardingMetricsService = mockk()
    private val myCompetencyService: MyCompetencyService = mockk()
    private val starterWorkTaskProposalService: StarterWorkTaskProposalService = mockk()
    private val knowledgeBaseService: KnowledgeBaseService = mockk()
    private val userApi: UserApi = mockk()
    private val buddyPlanTools: BuddyPlanTools = mockk(relaxed = true)
    private val buddyBoardTools: BuddyBoardTools = mockk(relaxed = true)
    private val artifactIngestionApi: ArtifactIngestionApi = mockk()
    private val projectMembershipApi: ProjectMembershipApi = mockk()

    // Defaults to "nobody has authored an arrival list", which is what a fresh install looks
    // like.
    private val arrivalStepService: ArrivalStepService = mockk {
        every { forHire(any()) } returns emptyList()
    }

    // Defaults to the engineering track, so these tests describe the behaviour every existing hire
    // gets; the track-specific mounting is exercised in TrackServiceTest and its own cases below.
    private val trackService: TrackService = mockk {
        every { admitsAnywhere(any(), any()) } returns true
    }

    private val executor = BuddyToolExecutor(
        onboardingMetricsService,
        myCompetencyService,
        starterWorkTaskProposalService,
        knowledgeBaseService,
        userApi,
        buddyPlanTools,
        buddyBoardTools,
        // The real reader, not a mock: which pull requests count as open and which one leads are
        // its judgements now, and these tests are about the buddy still reporting them.
        OpenPullRequestReader(artifactIngestionApi),
        trackService,
        projectMembershipApi,
        arrivalStepService,
    )

    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val metricsCall = BuddyToolCallDto(id = "c0", name = "get_my_metrics")
    private val competenciesCall = BuddyToolCallDto(id = "c0", name = "get_my_competencies")
    private val openPullRequestsCall = BuddyToolCallDto(id = "c0", name = "get_my_open_pull_requests")
    private val suggestedTasksCall = BuddyToolCallDto(id = "c0", name = "get_suggested_tasks")
    private val arrivalCall = BuddyToolCallDto(id = "c0", name = "get_arrival_steps")

    private fun resolvedStep(
        key: String = "vpn",
        title: String = "Request VPN access",
        description: String? = null,
        href: String? = null,
        selfConfirmable: Boolean = true,
        rigor: Rigor? = null,
    ) = ResolvedArrivalStep(
        step = ArrivalStep(
            key = key,
            title = title,
            description = description,
            href = href,
            selfConfirmable = selfConfirmable,
        ),
        settledAt = rigor?.let { Instant.now() },
        rigor = rigor,
    )

    private fun openPullRequest(
        number: Int?,
        title: String?,
        openedHoursAgo: Long?,
        firstResponseAt: Instant? = null,
        sourceUrl: String? = null,
        state: String = "OPEN",
    ) = AuthoredPullRequest(
        artifactId = UUID.randomUUID(),
        openedAt = openedHoursAgo?.let { Instant.now().minusSeconds(it * 3600) },
        firstResponseAt = firstResponseAt,
        mergedAt = null,
        state = state,
        number = number,
        title = title,
        sourceUrl = sourceUrl,
    )

    private fun canonicalSearchCall(query: String) = BuddyToolCallDto(
        id = "c0",
        name = "search_canonical_answers",
        arguments = buildJsonObject { put("query", query) },
    )

    private fun userWith(vararg projects: ProjectDto) = UserDto(
        id = userId,
        username = "hire",
        firstname = "Sam",
        lastname = "Hire",
        avatarUrl = null,
        profileIcon = null,
        projects = projects.toSet(),
        projectRoles = emptyList(),
    )

    private fun timeline(
        openPrs: Int = 1,
        longestOpenWaitHours: Long? = 52,
        stalled: Boolean = true,
        stalledReason: String? = "waiting on a review",
    ) = HireTimelineResponse(
        userId = userId,
        displayName = "Sam Hire",
        githubLogin = "sam",
        joinedAt = null,
        taskZeroAssignedAt = null,
        firstTaskClaimedAt = null,
        firstContributionOpenedAt = null,
        firstResponseAt = null,
        firstContributionAcceptedAt = null,
        hoursToFirstAcceptedContribution = null,
        hoursToFirstResponse = null,
        acceptedContributionCount = 0,
        openContributionCount = openPrs,
        longestOpenWaitHours = longestOpenWaitHours,
        stalled = stalled,
        stalledReason = stalledReason,
        autonomyReachedAt = null,
        vocabulary = HireVocabularyResponse(
            trackLabel = "Engineering",
            contributionNoun = "change",
            contributionNounPlural = "changes",
            contributionVerbPast = "merged",
        ),
        returnedContributionCount = 0,
    )

    private fun competency(
        label: String,
        level: Int,
        targetLevel: Int,
    ) = MyCompetencyResponse(
        competencyKey = label.lowercase(),
        label = label,
        kind = CompetencyKind.SKILL,
        level = level,
        targetLevel = targetLevel,
        source = CompetencySource.VERIFIED,
        updatedAt = Instant.EPOCH,
    )

    private fun rankedTask(
        title: String,
        reasons: List<String>,
        sourceUrl: String? = null,
    ) = RankedStarterWorkTaskResponse(
        task = StarterWorkTaskProposalResponse(
            id = UUID.randomUUID(),
            sourceId = "src-$title",
            title = title,
            summary = null,
            rationale = null,
            sourceUrl = sourceUrl,
            competencyKeys = emptyList(),
            status = ProposalStatus.LIVE,
            taskZeroEligible = false,
        ),
        score = 1.0,
        matchedCompetencyKeys = emptyList(),
        taskType = TaskType.BUG,
        reasons = reasons,
    )

    @Test
    fun `exposes the caller-scoped hire-state tools`() {
        every { buddyPlanTools.toolSpecs() } returns emptyList()

        assertThat(executor.toolSpecs(userId).map { it.name }).containsExactly(
            "get_my_metrics",
            "get_my_competencies",
            "get_my_open_pull_requests",
            "get_suggested_tasks",
            "search_canonical_answers",
            "get_teammates",
        )
    }

    /**
     * Absent, never empty — the same gate the board card uses, read from the same service. A tool
     * that can only ever answer "nobody has written a list" invites the mentor to open with it.
     */
    @Test
    fun `a hire with no arrival list is not offered the arrival tool`() {
        every { buddyPlanTools.toolSpecs() } returns emptyList()

        assertThat(executor.toolSpecs(userId).map { it.name }).doesNotContain("get_arrival_steps")
    }

    /**
     * **First in the list, and that is the point of the slice.** The failure this initiative exists
     * to fix is somebody who cannot clone the repository being handed a good first issue.
     */
    @Test
    fun `arrival comes before every other hire-state tool`() {
        every { buddyPlanTools.toolSpecs() } returns emptyList()
        every { arrivalStepService.forHire(userId) } returns listOf(resolvedStep())

        assertThat(executor.toolSpecs(userId).map { it.name }).startsWith("get_arrival_steps")
    }

    @Test
    fun `lists what is outstanding and how each settled step was established`() {
        every { arrivalStepService.forHire(userId) } returns listOf(
            resolvedStep(key = "vpn", title = "Request VPN access"),
            resolvedStep(key = "github-account", title = "Add your GitHub username", rigor = Rigor.OBSERVED),
            resolvedStep(key = "laptop", title = "Collect a laptop", rigor = Rigor.DECLARED),
        )

        val result = executor.execute(arrivalCall, userId)

        assertThat(result).contains("Request VPN access")
        // Attribution, not a tick: what the system confirmed and what the hire asserted are
        // different facts, and the model must be able to tell them apart in its own words.
        assertThat(result).contains("Add your GitHub username (we confirmed this)")
        assertThat(result).contains("Collect a laptop (they told us)")
    }

    /**
     * ⚠️ The `progressPercentage` defect, relocated: a model handed "1 of 3 done" over a mix of
     * observed and declared steps will repeat it, and the number means nothing.
     */
    @Test
    fun `never gives the model a blended total to repeat`() {
        every { arrivalStepService.forHire(userId) } returns listOf(
            resolvedStep(key = "vpn", title = "Request VPN access"),
            resolvedStep(key = "laptop", title = "Collect a laptop", rigor = Rigor.DECLARED),
            resolvedStep(key = "badge", title = "Collect a badge", rigor = Rigor.OBSERVED),
        )

        val result = executor.execute(arrivalCall, userId)

        assertThat(result).doesNotContainPattern("\\d+\\s*(of|/)\\s*\\d+")
        assertThat(result).doesNotContain("%")
    }

    /**
     * Nothing here gates anything, and the model's own words are the one place that could
     * reintroduce the gate without any code saying so.
     */
    @Test
    fun `says outright that an outstanding step does not stop them working`() {
        every { arrivalStepService.forHire(userId) } returns listOf(resolvedStep())

        assertThat(executor.execute(arrivalCall, userId))
            .contains("none of this stops them working")
    }

    /**
     * The backend refuses a confirmation on a step that is not self-confirmable, so a buddy that
     * offered to tick one would produce an error the hire never caused.
     */
    @Test
    fun `warns the mentor off offering to tick a step only the system settles`() {
        every { arrivalStepService.forHire(userId) } returns listOf(
            resolvedStep(key = "github-account", title = "Add your GitHub username", selfConfirmable = false),
        )

        assertThat(executor.execute(arrivalCall, userId)).contains("they cannot mark it done")
    }

    /**
     * ⚠️ Order is the feature. A greeting grounded in progress before setup is exactly the failure
     * this initiative exists to fix — and it reads as calm, because the stall detector watches
     * contributions rather than access.
     */
    @Test
    fun `the opening greeting is grounded in what is missing before anything else`() {
        every { arrivalStepService.forHire(userId) } returns listOf(resolvedStep())
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())
        // No projects, so metrics and suggestions short-circuit; only the ledger is reached.
        every { myCompetencyService.getCompetenciesForUser(userId) } returns emptyList()
        every { userApi.getGithubLoginByUserId(userId) } returns null

        val snapshot = executor.stateSnapshot(userId)

        assertThat(snapshot).startsWith("Before they can work:")
        assertThat(snapshot.indexOf("Before they can work:"))
            .isLessThan(snapshot.indexOf("Progress:"))
    }

    @Test
    fun `a hire with no arrival list gets no arrival section in the greeting`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())
        // No projects, so metrics and suggestions short-circuit; only the ledger is reached.
        every { myCompetencyService.getCompetenciesForUser(userId) } returns emptyList()
        every { userApi.getGithubLoginByUserId(userId) } returns null

        // Not an empty section: a greeting grounded in "arrival: nothing" finds something to say
        // about it, which is the pull-request card's lesson applied here.
        assertThat(executor.stateSnapshot(userId)).doesNotContain("Before they can work:")
    }

    @Test
    fun `a hire whose track cannot have pull requests is not offered the pull-request tool`() {
        every { buddyPlanTools.toolSpecs() } returns emptyList()
        every { trackService.admitsAnywhere(userId, ContributionEvidenceKind.PULL_REQUEST) } returns false

        // Absent, not present-and-empty: a mentor offered a tool for evidence this role can never
        // produce will open the conversation by asking about it.
        assertThat(executor.toolSpecs(userId).map { it.name }).containsExactly(
            "get_my_metrics",
            "get_my_competencies",
            "get_suggested_tasks",
            "search_canonical_answers",
            "get_teammates",
        )
    }

    @Test
    fun `a hire whose track cannot have attested work is not offered the teammates tool`() {
        every { buddyPlanTools.toolSpecs() } returns emptyList()
        every { trackService.admitsAnywhere(userId, ContributionEvidenceKind.ATTESTATION) } returns false

        // Naming teammates is only useful to a hire who can ask one of them to confirm something.
        assertThat(executor.toolSpecs(userId).map { it.name }).doesNotContain("get_teammates")
    }

    @Test
    fun `names the hire's open pull requests, longest wait first, with links`() {
        every { userApi.getGithubLoginByUserId(userId) } returns "sam"
        every { userApi.getUsersByIds(listOf(userId)) } returns
            listOf(userWith(ProjectDto(projectId, "Checkout", null)))
        every { artifactIngestionApi.getAuthoredPullRequests(projectId, "sam") } returns listOf(
            openPullRequest(
                number = 128,
                title = "Add null-reviewer guard",
                openedHoursAgo = 10,
                sourceUrl = "https://example.test/pull/128",
            ),
            openPullRequest(number = 141, title = "Diff scoring", openedHoursAgo = 1514),
        )

        val result = executor.execute(openPullRequestsCall, userId)

        assertThat(result).contains("#128 Add null-reviewer guard")
        assertThat(result).contains("#141 Diff scoring")
        assertThat(result).contains("https://example.test/pull/128")
        assertThat(result).contains("waiting 1514 hours for a first review")
        // Longest-waiting (the 1514h one) is listed before the 10h one.
        assertThat(result.indexOf("#141")).isLessThan(result.indexOf("#128"))
    }

    @Test
    fun `does not list a pull request closed without merging as open`() {
        every { userApi.getGithubLoginByUserId(userId) } returns "sam"
        every { userApi.getUsersByIds(listOf(userId)) } returns
            listOf(userWith(ProjectDto(projectId, "Checkout", null)))
        every { artifactIngestionApi.getAuthoredPullRequests(projectId, "sam") } returns listOf(
            openPullRequest(number = 128, title = "Abandoned attempt", openedHoursAgo = 100, state = "CLOSED"),
        )

        val result = executor.execute(openPullRequestsCall, userId)

        assertThat(result).contains("no open pull requests")
        assertThat(result).doesNotContain("#128")
    }

    @Test
    fun `does not call a pull request answered by someone as still waiting`() {
        every { userApi.getGithubLoginByUserId(userId) } returns "sam"
        every { userApi.getUsersByIds(listOf(userId)) } returns
            listOf(userWith(ProjectDto(projectId, "Checkout", null)))
        every { artifactIngestionApi.getAuthoredPullRequests(projectId, "sam") } returns listOf(
            openPullRequest(
                number = 128,
                title = "Add null-reviewer guard",
                openedHoursAgo = 50,
                firstResponseAt = Instant.now(),
            ),
        )

        val result = executor.execute(openPullRequestsCall, userId)

        assertThat(result).contains("#128 Add null-reviewer guard")
        assertThat(result).doesNotContain("waiting")
    }

    @Test
    fun `asks for a GitHub username before it can list pull requests`() {
        every { userApi.getGithubLoginByUserId(userId) } returns null

        val result = executor.execute(openPullRequestsCall, userId)

        assertThat(result).contains("GitHub username")
    }

    @Test
    fun `says plainly when there are no open pull requests`() {
        every { userApi.getGithubLoginByUserId(userId) } returns "sam"
        every { userApi.getUsersByIds(listOf(userId)) } returns
            listOf(userWith(ProjectDto(projectId, "Checkout", null)))
        every { artifactIngestionApi.getAuthoredPullRequests(projectId, "sam") } returns emptyList()

        val result = executor.execute(openPullRequestsCall, userId)

        assertThat(result).contains("no open pull requests")
    }

    @Test
    fun `aggregates the plan tools into the catalog and delegates their execution`() {
        val planSpec = BuddyToolSpecDto(
            name = "get_learning_plan",
            description = "plan",
            parameters = buildJsonObject { },
        )
        val planCall = BuddyToolCallDto(id = "c9", name = "get_learning_plan")
        every { buddyPlanTools.toolSpecs() } returns listOf(planSpec)
        every { buddyPlanTools.handles("get_learning_plan") } returns true
        every { buddyPlanTools.execute(planCall, userId) } returns "the plan"

        assertThat(executor.toolSpecs(userId).map { it.name }).contains("get_learning_plan")
        assertThat(executor.execute(planCall, userId)).isEqualTo("the plan")
    }

    @Test
    fun `serves a teammate's canonical answer faithfully`() {
        every { knowledgeBaseService.searchForUser(userId, "how do we deploy") } returns listOf(
            CanonicalAnswer(
                projectId = projectId,
                question = "How do we deploy?",
                answer = "Run ./deploy.sh from main after CI is green.",
                authorId = UUID.randomUUID(),
            ),
        )

        val result = executor.execute(canonicalSearchCall("how do we deploy"), userId)

        assertThat(result).contains("How do we deploy?")
        assertThat(result).contains("Run ./deploy.sh from main after CI is green.")
    }

    @Test
    fun `reports no canonical answer so the buddy knows to suggest escalation`() {
        every { knowledgeBaseService.searchForUser(userId, "obscure thing") } returns emptyList()

        val result = executor.execute(canonicalSearchCall("obscure thing"), userId)

        assertThat(result).contains("No teammate has answered")
    }

    @Test
    fun `describes the wait and stall for a project the hire is on`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns
            listOf(userWith(ProjectDto(projectId, "Checkout", null)))
        every { onboardingMetricsService.getHireTimeline(userId, projectId) } returns timeline()

        val result = executor.execute(metricsCall, userId)

        assertThat(result).contains("Checkout")
        assertThat(result).contains("52 hours")
        assertThat(result).contains("waiting on a review")
    }

    @Test
    fun `says so plainly when the hire is on no project`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())

        val result = executor.execute(metricsCall, userId)

        assertThat(result).contains("not a member of any project")
    }

    @Test
    fun `separates held competencies from those still below target`() {
        every { myCompetencyService.getCompetenciesForUser(userId) } returns listOf(
            competency("Kotlin", level = 3, targetLevel = 2),
            competency("React", level = 1, targetLevel = 3),
        )

        val result = executor.execute(competenciesCall, userId)

        assertThat(result).contains("Competencies held (meet their target level): 1")
        assertThat(result).contains("Kotlin (level 3/2)")
        assertThat(result).contains("Below target")
        assertThat(result).contains("React (level 1/3)")
    }

    @Test
    fun `excludes level-0 placed-but-unknown rows from the ledger`() {
        every { myCompetencyService.getCompetenciesForUser(userId) } returns listOf(
            competency("Docker", level = 0, targetLevel = 2),
        )

        val result = executor.execute(competenciesCall, userId)

        assertThat(result).contains("no demonstrated competencies")
    }

    @Test
    fun `suggests ranked tasks with their reasons, their claim ids, and never a score`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns
            listOf(userWith(ProjectDto(projectId, "Checkout", null)))
        val task = rankedTask(
            "Fix the login redirect",
            reasons = listOf("Matches a skill you hold (Kotlin)", "Labelled 'good first issue'"),
            sourceUrl = "https://example.test/issues/1",
        )
        every { starterWorkTaskProposalService.matchForUserId(userId, projectId) } returns listOf(task)

        val result = executor.execute(suggestedTasksCall, userId)

        assertThat(result).contains("Checkout")
        assertThat(result).contains("Fix the login redirect")
        assertThat(result).contains("Matches a skill you hold (Kotlin)")
        assertThat(result).contains("https://example.test/issues/1")
        // The id the claim_goal action names the task by...
        assertThat(result).contains("[task_id: ${task.task.id}]")
        // ...and the ranker's score must never surface — a number is not a reason a hire can act on.
        assertThat(result).doesNotContain("1.0")
    }

    @Test
    fun `says so when there are no approved tasks to suggest`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns
            listOf(userWith(ProjectDto(projectId, "Checkout", null)))
        every { starterWorkTaskProposalService.matchForUserId(userId, projectId) } returns emptyList()

        val result = executor.execute(suggestedTasksCall, userId)

        assertThat(result).contains("no starter-work tasks")
    }

    @Test
    fun `reports an unknown tool rather than throwing`() {
        val result = executor.execute(BuddyToolCallDto(id = "c1", name = "launch_rockets"), userId)

        assertThat(result).contains("Unknown tool")
    }
}
