package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.IngestedIssue
import com.sprintstart.sprintstartbackend.ingestion.external.RepositoryResponsiveness
import com.sprintstart.sprintstartbackend.ingestion.external.TaskSourceArtifact
import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CandidatePoolState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.ProposedStarterTaskSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.StarterWorkOutcome
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.GithubHistoryPrior
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState
import com.sprintstart.sprintstartbackend.onboarding.model.request.starterwork.CreateStarterWorkTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.starterwork.PromoteStarterWorkCandidateRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StarterWorkTaskProposalServiceTest {
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val competencyRepository: CompetencyRepository = mockk()
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository = mockk()
    private val userCompetencyStateRepository: UserCompetencyStateRepository = mockk()
    private val githubHistoryPriorService: GithubHistoryPriorService = mockk()
    private val artifactIngestionApi: ArtifactIngestionApi = mockk()
    private val userApi: UserApi = mockk()
    private val projectMembershipApi: ProjectMembershipApi = mockk(relaxed = true)
    private val trackService: TrackService = mockk(relaxed = true)
    private val json: Json = Json { ignoreUnknownKeys = true }
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)
    private val service = StarterWorkTaskProposalService(
        onboardingAiClient,
        competencyRepository,
        starterWorkTaskProposalRepository,
        userCompetencyStateRepository,
        githubHistoryPriorService,
        artifactIngestionApi,
        userApi,
        projectMembershipApi,
        trackService,
        json,
        transactionManager,
    )

    @Nested
    inner class Generate {
        @Test
        fun `persists proposed tasks as PROPOSED rows`() = runTest {
            every { starterWorkTaskProposalRepository.findAllByStatusIn(any()) } returns emptyList()
            every { competencyRepository.findAll() } returns emptyList()
            coEvery { onboardingAiClient.proposeStarterWork(any(), any()) } returns
                StarterWorkOutcome(
                    status = "proposed",
                    tasks = listOf(
                        ProposedStarterTaskSchema(
                            sourceId = "github:org/repo:ISSUE:1",
                            title = "Fix typo",
                            summary = "Fix a typo in the README.",
                            competencyKeys = listOf("docs"),
                            rationale = "Small, well-scoped.",
                        ),
                    ),
                )
            val slot = slot<StarterWorkTaskProposal>()
            every { starterWorkTaskProposalRepository.save(capture(slot)) } answers { slot.captured }

            val result = service.generate()

            assertEquals("github:org/repo:ISSUE:1", slot.captured.sourceId)
            assertEquals("Fix typo", slot.captured.title)
            assertEquals(listOf("docs"), slot.captured.competencyKeys)
            // Claimable on arrival, and honest that nobody has looked at it yet.
            assertEquals(ProposalStatus.LIVE, slot.captured.status)
            assertEquals(false, slot.captured.reviewed)
            assertEquals(1, result.tasksProposed)
            assertEquals("proposed", result.status)
        }

        /**
         * The property that makes a rejection worth making: mining consults rejected rows, so a
         * task somebody turned down is never mined back into existence. Without it they turn it
         * down again after every crawl.
         */
        @Test
        fun `a rejected task is never mined back into existence`() = runTest {
            every {
                starterWorkTaskProposalRepository.findAllByStatusIn(
                    listOf(ProposalStatus.LIVE, ProposalStatus.REJECTED),
                )
            } returns listOf(
                StarterWorkTaskProposal(
                    sourceId = "github:org/repo:ISSUE:1",
                    title = "Nobody wanted this",
                    status = ProposalStatus.REJECTED,
                ),
            )
            every { competencyRepository.findAll() } returns emptyList()
            coEvery { onboardingAiClient.proposeStarterWork(any(), any()) } returns
                StarterWorkOutcome(
                    status = "proposed",
                    tasks = listOf(
                        ProposedStarterTaskSchema(
                            sourceId = "github:org/repo:ISSUE:1",
                            title = "Nobody wanted this",
                            summary = "",
                            competencyKeys = emptyList(),
                            rationale = "",
                        ),
                    ),
                )

            val result = service.generate()

            assertEquals(0, result.tasksProposed)
            verify(exactly = 0) { starterWorkTaskProposalRepository.save(any()) }
        }

        fun `sends the pooled source ids and the live competency keys`() = runTest {
            val pooledStatuses = listOf(ProposalStatus.LIVE, ProposalStatus.REJECTED)
            every { starterWorkTaskProposalRepository.findAllByStatusIn(pooledStatuses) } returns
                listOf(
                    StarterWorkTaskProposal(sourceId = "s1", title = "t1", status = ProposalStatus.LIVE),
                    StarterWorkTaskProposal(sourceId = "s2", title = "t2", status = ProposalStatus.REJECTED),
                )
            every { competencyRepository.findAll() } returns
                listOf(Competency(key = "kotlin", label = "Kotlin", kind = CompetencyKind.SKILL))
            val sourceIdsSlot = slot<List<String>>()
            val keysSlot = slot<List<String>>()
            coEvery {
                onboardingAiClient.proposeStarterWork(capture(sourceIdsSlot), capture(keysSlot))
            } returns StarterWorkOutcome(status = "unchanged")

            service.generate()

            assertEquals(listOf("s1", "s2"), sourceIdsSlot.captured)
            assertEquals(listOf("kotlin"), keysSlot.captured)
        }
    }

    @Nested
    inner class StreamGenerate {
        private fun proposedOutcome() = StarterWorkOutcome(
            status = "proposed",
            tasks = listOf(
                ProposedStarterTaskSchema(
                    sourceId = "github:org/repo:ISSUE:1",
                    title = "Fix typo",
                    summary = "Fix a typo.",
                    competencyKeys = listOf("docs"),
                    rationale = "Small.",
                ),
            ),
        )

        private fun doneEvent(outcome: StarterWorkOutcome) = AiProgressEvent(
            type = "done",
            operation = "starter_work",
            result = json.encodeToJsonElement(outcome),
        )

        @Test
        fun `relays the events and persists the tasks on done`() = runTest {
            every { starterWorkTaskProposalRepository.findAllByStatusIn(any()) } returns emptyList()
            every { competencyRepository.findAll() } returns emptyList()
            every { starterWorkTaskProposalRepository.save(any()) } answers { firstArg() }
            every { onboardingAiClient.streamStarterWork(any(), any()) } returns flowOf(
                AiProgressEvent(type = "stage", operation = "starter_work", stage = "retrieving", label = "…"),
                AiProgressEvent(type = "item", operation = "starter_work", label = "Task: Fix typo"),
                doneEvent(proposedOutcome()),
            )

            val events = service.streamGenerate().toList()

            assertEquals(listOf("stage", "item", "done"), events.map { it.type })
            verify(exactly = 1) { starterWorkTaskProposalRepository.save(any()) }
        }

        @Test
        fun `a task already in the pool becomes a warning before the done`() = runTest {
            // The issue is already pooled -> the backend gate skips it and must announce it.
            every { starterWorkTaskProposalRepository.findAllByStatusIn(any()) } returns listOf(
                StarterWorkTaskProposal(
                    sourceId = "github:org/repo:ISSUE:1",
                    title = "Fix typo",
                    status = ProposalStatus.LIVE,
                ),
            )
            every { competencyRepository.findAll() } returns emptyList()
            every { onboardingAiClient.streamStarterWork(any(), any()) } returns flowOf(doneEvent(proposedOutcome()))

            val events = service.streamGenerate().toList()

            assertEquals(listOf("warning", "done"), events.map { it.type })
            verify(exactly = 0) { starterWorkTaskProposalRepository.save(any()) }
        }

        @Test
        fun `a stream failure becomes a synthesised terminal error`() = runTest {
            every { starterWorkTaskProposalRepository.findAllByStatusIn(any()) } returns emptyList()
            every { competencyRepository.findAll() } returns emptyList()
            every { onboardingAiClient.streamStarterWork(any(), any()) } returns
                flow { throw RuntimeException("ai down") }

            val events = service.streamGenerate().toList()

            assertEquals("error", events.last().type)
        }
    }

    @Nested
    inner class ListProposed {
        @Test
        fun `returns PROPOSED tasks`() {
            every { starterWorkTaskProposalRepository.findAllByStatusAndReviewedFalse(ProposalStatus.LIVE) } returns
                listOf(StarterWorkTaskProposal(sourceId = "s1", title = "t1"))

            val result = service.listUnreviewed()

            assertEquals(1, result.tasks.size)
        }
    }

    @Nested
    inner class Approve {
        @Test
        fun `throws 404 when no proposal matches`() {
            val id = UUID.randomUUID()
            every { starterWorkTaskProposalRepository.findById(id) } returns Optional.empty()

            val ex = assertThrows<ResponseStatusException> { service.markReviewed(id) }

            assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
        }

        @Test
        fun `throws 409 when the proposal was already decided`() {
            val proposal = StarterWorkTaskProposal(sourceId = "s1", title = "t1", status = ProposalStatus.REJECTED)
            every { starterWorkTaskProposalRepository.findById(proposal.id) } returns Optional.of(proposal)

            val ex = assertThrows<ResponseStatusException> { service.markReviewed(proposal.id) }

            assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        }
    }

    @Nested
    inner class CreateTask {
        @Test
        fun `rejects a blank title`() {
            val ex = assertThrows<ResponseStatusException> {
                service.createTask(CreateStarterWorkTaskRequest(title = "   "))
            }

            assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
            verify(exactly = 0) { starterWorkTaskProposalRepository.save(any()) }
        }
    }

    @Nested
    inner class ListCandidates {
        private val projectId = UUID.randomUUID()

        @Test
        fun `marks each issue with where it stands in the pool`() {
            every { starterWorkTaskProposalRepository.findAllByStatusIn(any()) } returns listOf(
                StarterWorkTaskProposal(sourceId = "i:2", title = "pooled", status = ProposalStatus.LIVE),
                StarterWorkTaskProposal(sourceId = "i:3", title = "gone", status = ProposalStatus.REJECTED),
            )
            every { artifactIngestionApi.getOpenIssues(projectId) } returns listOf(
                ingestedIssue("i:1"),
                ingestedIssue("i:2"),
                ingestedIssue("i:3"),
            )

            val byId = service.listCandidates(projectId).associateBy { it.sourceId }

            assertEquals(CandidatePoolState.AVAILABLE, byId.getValue("i:1").poolState)
            assertEquals(CandidatePoolState.IN_POOL, byId.getValue("i:2").poolState)
            assertEquals(CandidatePoolState.REMOVED, byId.getValue("i:3").poolState)
        }

        /**
         * ⚠️ Nothing is dropped for being taken, pooled or removed. A person browsing has to be
         * able to tell "somebody has this" from "not ingested", and an issue that is simply absent
         * from the list says neither.
         */
        @Test
        fun `keeps assigned issues in the list, carrying the three-valued flag`() {
            every { starterWorkTaskProposalRepository.findAllByStatusIn(any()) } returns emptyList()
            every { artifactIngestionApi.getOpenIssues(projectId) } returns listOf(
                ingestedIssue("i:1", hasAssignee = true),
                ingestedIssue("i:2", hasAssignee = false),
                ingestedIssue("i:3", hasAssignee = null),
            )

            val result = service.listCandidates(projectId).associate { it.sourceId to it.hasAssignee }

            assertEquals(mapOf("i:1" to true, "i:2" to false, "i:3" to null), result)
        }

        @Test
        fun `says when it cut a long body, and does not when it did not`() {
            every { starterWorkTaskProposalRepository.findAllByStatusIn(any()) } returns emptyList()
            every { artifactIngestionApi.getOpenIssues(projectId) } returns listOf(
                ingestedIssue("i:1", body = "x".repeat(2000)),
                ingestedIssue("i:2", body = "short"),
            )

            val byId = service.listCandidates(projectId).associateBy { it.sourceId }

            assertTrue(byId.getValue("i:1").excerptTruncated)
            assertEquals(600, byId.getValue("i:1").excerpt?.length)
            assertEquals(false, byId.getValue("i:2").excerptTruncated)
            assertEquals("short", byId.getValue("i:2").excerpt)
        }

        @Test
        fun `orders newest-changed first, breaking ties on source id`() {
            every { starterWorkTaskProposalRepository.findAllByStatusIn(any()) } returns emptyList()
            every { artifactIngestionApi.getOpenIssues(projectId) } returns listOf(
                ingestedIssue("i:b", updatedAt = Instant.parse("2026-08-01T00:00:00Z")),
                ingestedIssue("i:c", updatedAt = Instant.parse("2026-08-05T00:00:00Z")),
                ingestedIssue("i:a", updatedAt = Instant.parse("2026-08-01T00:00:00Z")),
            )

            assertEquals(
                listOf("i:c", "i:a", "i:b"),
                service.listCandidates(projectId).map { it.sourceId },
            )
        }

        @Test
        fun `drops an issue with no title, since there is nothing to pick from`() {
            every { starterWorkTaskProposalRepository.findAllByStatusIn(any()) } returns emptyList()
            every { artifactIngestionApi.getOpenIssues(projectId) } returns listOf(
                ingestedIssue("i:1", title = null),
                ingestedIssue("i:2", title = "   "),
                ingestedIssue("i:3"),
            )

            assertEquals(listOf("i:3"), service.listCandidates(projectId).map { it.sourceId })
        }
    }

    @Nested
    inner class PromoteCandidate {
        @Test
        fun `lands live and reviewed, taking its title and link from the ingested issue`() {
            every { artifactIngestionApi.getIssue("i:1") } returns ingestedIssue("i:1", title = "Fix the typo")
            every { starterWorkTaskProposalRepository.findBySourceId("i:1") } returns null
            val saved = slot<StarterWorkTaskProposal>()
            every { starterWorkTaskProposalRepository.save(capture(saved)) } answers { saved.captured }

            val result = service.promoteCandidate(
                PromoteStarterWorkCandidateRequest(sourceId = "i:1", competencyKeys = listOf("docs")),
            )

            assertEquals("Fix the typo", saved.captured.title)
            assertEquals("https://example.test/i:1", saved.captured.sourceUrl)
            assertEquals(ProposalStatus.LIVE, saved.captured.status)
            assertTrue(saved.captured.reviewed)
            assertEquals(listOf("docs"), saved.captured.competencyKeys)
            assertEquals("i:1", result.sourceId)
        }

        /**
         * ⚠️ The issue's own body is deliberately not copied into `summary`: orientation reads that
         * text live from the corpus, so a copy taken at promotion time is a second version of it
         * that quietly goes stale.
         */
        @Test
        fun `carries only the promoter's own note, never the issue body`() {
            every { artifactIngestionApi.getIssue("i:1") } returns ingestedIssue("i:1", body = "the issue body")
            every { starterWorkTaskProposalRepository.findBySourceId("i:1") } returns null
            val saved = slot<StarterWorkTaskProposal>()
            every { starterWorkTaskProposalRepository.save(capture(saved)) } answers { saved.captured }

            service.promoteCandidate(PromoteStarterWorkCandidateRequest(sourceId = "i:1"))

            assertNull(saved.captured.summary)
            assertNull(saved.captured.rationale)
        }

        /**
         * ⚠️ Mining skips assigned issues because it cannot ask anybody. Somebody promoting one has
         * seen who holds it, and that override is why the browser shows them at all.
         */
        @Test
        fun `accepts an issue somebody else is assigned`() {
            every { artifactIngestionApi.getIssue("i:1") } returns ingestedIssue("i:1", hasAssignee = true)
            every { starterWorkTaskProposalRepository.findBySourceId("i:1") } returns null
            every { starterWorkTaskProposalRepository.save(any()) } answers { firstArg() }

            val result = service.promoteCandidate(PromoteStarterWorkCandidateRequest("i:1"))

            assertEquals(ProposalStatus.LIVE, result.status)
        }

        @Test
        fun `404s when nothing with that source id is ingested`() {
            every { artifactIngestionApi.getIssue("i:404") } returns null

            val ex = assertThrows<ResponseStatusException> {
                service.promoteCandidate(PromoteStarterWorkCandidateRequest("i:404"))
            }

            assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
            verify(exactly = 0) { starterWorkTaskProposalRepository.save(any()) }
        }

        @Test
        fun `409s rather than writing a second row for an issue already in the pool`() {
            every { artifactIngestionApi.getIssue("i:1") } returns ingestedIssue("i:1")
            every { starterWorkTaskProposalRepository.findBySourceId("i:1") } returns
                StarterWorkTaskProposal(sourceId = "i:1", title = "already here", status = ProposalStatus.LIVE)

            val ex = assertThrows<ResponseStatusException> {
                service.promoteCandidate(PromoteStarterWorkCandidateRequest("i:1"))
            }

            assertEquals(HttpStatus.CONFLICT, ex.statusCode)
            assertContains(ex.reason.orEmpty(), "already in the starter-work pool")
            verify(exactly = 0) { starterWorkTaskProposalRepository.save(any()) }
        }

        /**
         * ⚠️ Rejection is sticky on purpose — a task somebody removed is never mined back, or they
         * would remove it again after every crawl. Promotion must not become the accidental way
         * round that.
         */
        @Test
        fun `refuses to revive an issue somebody removed from the pool`() {
            every { artifactIngestionApi.getIssue("i:1") } returns ingestedIssue("i:1")
            every { starterWorkTaskProposalRepository.findBySourceId("i:1") } returns
                StarterWorkTaskProposal(sourceId = "i:1", title = "removed", status = ProposalStatus.REJECTED)

            val ex = assertThrows<ResponseStatusException> {
                service.promoteCandidate(PromoteStarterWorkCandidateRequest("i:1"))
            }

            assertEquals(HttpStatus.CONFLICT, ex.statusCode)
            assertContains(ex.reason.orEmpty(), "not re-addable")
            verify(exactly = 0) { starterWorkTaskProposalRepository.save(any()) }
        }

        @Test
        fun `refuses an issue that is closed at its source`() {
            every { artifactIngestionApi.getIssue("i:1") } returns ingestedIssue("i:1", state = "CLOSED")

            val ex = assertThrows<ResponseStatusException> {
                service.promoteCandidate(PromoteStarterWorkCandidateRequest("i:1"))
            }

            assertEquals(HttpStatus.CONFLICT, ex.statusCode)
            verify(exactly = 0) { starterWorkTaskProposalRepository.save(any()) }
        }

        /**
         * An unknown state is not a closed one. Refusing on null would make an un-promotable issue
         * out of every row ingested before state was captured.
         */
        @Test
        fun `accepts an issue whose state was never captured`() {
            every { artifactIngestionApi.getIssue("i:1") } returns ingestedIssue("i:1", state = null)
            every { starterWorkTaskProposalRepository.findBySourceId("i:1") } returns null
            every { starterWorkTaskProposalRepository.save(any()) } answers { firstArg() }

            val result = service.promoteCandidate(PromoteStarterWorkCandidateRequest("i:1"))

            assertEquals(ProposalStatus.LIVE, result.status)
        }

        @Test
        fun `400s when the ingested issue has no title`() {
            every { artifactIngestionApi.getIssue("i:1") } returns ingestedIssue("i:1", title = null)

            val ex = assertThrows<ResponseStatusException> {
                service.promoteCandidate(PromoteStarterWorkCandidateRequest("i:1"))
            }

            assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
            verify(exactly = 0) { starterWorkTaskProposalRepository.save(any()) }
        }
    }

    @Nested
    inner class Reject {
        @Test
        fun `marks the proposal REJECTED without touching the graph`() {
            val proposal = StarterWorkTaskProposal(sourceId = "s1", title = "t1")
            every { starterWorkTaskProposalRepository.findById(proposal.id) } returns Optional.of(proposal)

            val result = service.reject(proposal.id, "not relevant")

            assertEquals(ProposalStatus.REJECTED, proposal.status)
            assertEquals("not relevant", proposal.rejectionReason)
            assertEquals(ProposalStatus.REJECTED, result.status)
            verify(exactly = 0) { competencyRepository.save(any()) }
        }

        @Test
        fun `reason defaults to null`() {
            val proposal = StarterWorkTaskProposal(sourceId = "s1", title = "t1")
            every { starterWorkTaskProposalRepository.findById(proposal.id) } returns Optional.of(proposal)

            service.reject(proposal.id, null)

            assertNull(proposal.rejectionReason)
        }
    }

    private fun ingestedIssue(
        sourceId: String,
        title: String? = "An issue",
        body: String? = null,
        state: String? = "OPEN",
        hasAssignee: Boolean? = null,
        updatedAt: Instant? = Instant.parse("2026-08-01T00:00:00Z"),
    ) = IngestedIssue(
        sourceId = sourceId,
        tracker = "GITHUB",
        title = title,
        body = body,
        labels = listOf("good first issue"),
        sourceUrl = "https://example.test/$sourceId",
        state = state,
        hasAssignee = hasAssignee,
        updatedAtSource = updatedAt,
    )

    @Nested
    inner class MatchForUser {
        private val userId = UUID.randomUUID()
        private val projectId = UUID.randomUUID()

        private fun heldCompetency(key: String, level: Int = 3) =
            UserCompetencyState(
                userId = userId,
                competencyKey = key,
                level = level,
                source = CompetencySource.VERIFIED,
            )

        private fun pooledTask(sourceId: String, title: String, vararg keys: String) =
            StarterWorkTaskProposal(
                sourceId = sourceId,
                title = title,
                competencyKeys = keys.toMutableList(),
                status = ProposalStatus.LIVE,
            )

        private fun noHistory() {
            every { githubHistoryPriorService.getPrior(userId) } returns null
            every { artifactIngestionApi.getRepositoryResponsiveness(projectId) } returns emptyList()
            every { artifactIngestionApi.getTaskSource(any()) } returns null
        }

        @Test
        fun `ranks the approved pool for the resolved user`() {
            every { userApi.getUserIdByAuthId("auth-1") } returns Optional.of(userId)
            every { userCompetencyStateRepository.findAllByUserId(userId) } returns
                listOf(heldCompetency("kotlin"))
            every { starterWorkTaskProposalRepository.findAllByStatus(ProposalStatus.LIVE) } returns
                listOf(pooledTask("github:acme/api:ISSUE:1", "Task", "kotlin"))
            noHistory()

            val result = service.matchForUser("auth-1", projectId)

            assertEquals(1, result.size)
            assertEquals("github:acme/api:ISSUE:1", result[0].task.sourceId)
            assertEquals(listOf("kotlin"), result[0].matchedCompetencyKeys)
            // A suggestion nobody can interrogate is an instruction.
            assertTrue(result[0].reasons.isNotEmpty())
        }

        @Test
        fun `excludes level-0 (unplaced) ledger entries from the hire's competencies`() {
            every { userApi.getUserIdByAuthId("auth-1") } returns Optional.of(userId)
            every { userCompetencyStateRepository.findAllByUserId(userId) } returns
                listOf(
                    UserCompetencyState(
                        userId = userId,
                        competencyKey = "kotlin",
                        level = 0,
                        source = CompetencySource.ASSESSED,
                    ),
                    heldCompetency("git", level = 2),
                )
            every { starterWorkTaskProposalRepository.findAllByStatus(ProposalStatus.LIVE) } returns
                listOf(pooledTask("github:acme/api:ISSUE:1", "Unplaced", "kotlin"))
            noHistory()

            val result = service.matchForUser("auth-1", projectId)

            // "kotlin" is on the ledger but unplaced, so it must not count as competence held.
            assertEquals(emptyList(), result[0].matchedCompetencyKeys)
        }

        @Test
        fun `a hire with strong repo history ranks familiar work above unfamiliar work`() {
            every { userApi.getUserIdByAuthId("auth-1") } returns Optional.of(userId)
            every { userCompetencyStateRepository.findAllByUserId(userId) } returns emptyList()
            every { githubHistoryPriorService.getPrior(userId) } returns
                GithubHistoryPrior(
                    userId = userId,
                    signals = mutableMapOf("repo:acme/api" to 6, "label:bug" to 4),
                )
            every { artifactIngestionApi.getRepositoryResponsiveness(projectId) } returns emptyList()
            every { artifactIngestionApi.getTaskSource("github:acme/api:ISSUE:1") } returns
                TaskSourceArtifact(title = null, body = null, labels = listOf("bug"), sourceUrl = null)
            every { artifactIngestionApi.getTaskSource("github:acme/web:ISSUE:2") } returns
                TaskSourceArtifact(title = null, body = null, labels = listOf("feature"), sourceUrl = null)
            every { starterWorkTaskProposalRepository.findAllByStatus(ProposalStatus.LIVE) } returns
                listOf(
                    pooledTask("github:acme/web:ISSUE:2", "Elsewhere"),
                    pooledTask("github:acme/api:ISSUE:1", "Familiar"),
                )

            val result = service.matchForUser("auth-1", projectId)

            assertEquals("Familiar", result.first().task.title)
            assertContains(result.first().reasons.joinToString(), "acme/api")
        }

        @Test
        fun `a hire with no history still gets a ranked pool rather than nothing`() {
            every { userApi.getUserIdByAuthId("auth-1") } returns Optional.of(userId)
            every { userCompetencyStateRepository.findAllByUserId(userId) } returns emptyList()
            every { githubHistoryPriorService.getPrior(userId) } returns null
            every { artifactIngestionApi.getRepositoryResponsiveness(projectId) } returns emptyList()
            every { artifactIngestionApi.getTaskSource("github:acme/api:ISSUE:1") } returns
                TaskSourceArtifact(title = null, body = null, labels = listOf("refactor"), sourceUrl = null)
            every { artifactIngestionApi.getTaskSource("github:acme/api:ISSUE:2") } returns
                TaskSourceArtifact(title = null, body = null, labels = listOf("documentation"), sourceUrl = null)
            every { starterWorkTaskProposalRepository.findAllByStatus(ProposalStatus.LIVE) } returns
                listOf(
                    pooledTask("github:acme/api:ISSUE:1", "Refactor the scheduler"),
                    pooledTask("github:acme/api:ISSUE:2", "Fix a typo"),
                )

            val result = service.matchForUser("auth-1", projectId)

            // No consent and no ledger is "no evidence", never "beginner" -- but a forgiving first
            // task is still the better default.
            assertEquals("Fix a typo", result.first().task.title)
        }

        @Test
        fun `a slow repository demotes its task without hiding it`() {
            every { userApi.getUserIdByAuthId("auth-1") } returns Optional.of(userId)
            every { userCompetencyStateRepository.findAllByUserId(userId) } returns emptyList()
            every { githubHistoryPriorService.getPrior(userId) } returns null
            every { artifactIngestionApi.getRepositoryResponsiveness(projectId) } returns
                listOf(
                    RepositoryResponsiveness(
                        repositoryFullName = "acme/api",
                        medianHoursToFirstResponse = 300,
                        answeredCount = 2,
                        unansweredCount = 5,
                    ),
                )
            every { artifactIngestionApi.getTaskSource(any()) } returns null
            every { starterWorkTaskProposalRepository.findAllByStatus(ProposalStatus.LIVE) } returns
                listOf(pooledTask("github:acme/api:ISSUE:1", "Slow repo task"))

            val result = service.matchForUser("auth-1", projectId)

            // Still present -- a stale owner is a signal to a PM, not a reason to bury real work.
            assertEquals(1, result.size)
            assertContains(result[0].reasons.joinToString(), "reviews here take")
        }

        @Test
        fun `returns nothing when the pool is empty`() {
            every { userApi.getUserIdByAuthId("auth-1") } returns Optional.of(userId)
            every { starterWorkTaskProposalRepository.findAllByStatus(ProposalStatus.LIVE) } returns emptyList()

            val result = service.matchForUser("auth-1", projectId)

            assertTrue(result.isEmpty())
        }

        @Test
        fun `throws 404 when no user matches the auth id`() {
            every { userApi.getUserIdByAuthId("missing") } returns Optional.empty()

            val ex = assertThrows<ResponseStatusException> { service.matchForUser("missing", projectId) }

            assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
        }
    }
}
