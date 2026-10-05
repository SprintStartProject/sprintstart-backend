package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.QuestionAttemptRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.UserOnboardingProfile
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingPathServiceTest {
    private val onboardingPathRepository: OnboardingPathRepository = mockk()
    private val questionAttemptRepository: QuestionAttemptRepository = mockk(relaxed = true)
    private val userApi: UserApi = mockk()
    private val blueprintPathRepository: BlueprintPathRepository = mockk()

    // The real reader: it now owns the active-step and progress rules this service's team
    // overview reports, so stubbing it would hollow out exactly what these tests assert.
    private val service = OnboardingPathService(
        onboardingPathRepository,
        questionAttemptRepository,
        userApi,
        OnboardingPositionReader(onboardingPathRepository),
        blueprintPathRepository,
    )

    private val userId = UUID.randomUUID()
    private val pathId = UUID.randomUUID()
    private val authId = "auth|test-user"

    private fun makePath(id: UUID = pathId, uid: UUID = userId) =
        OnboardingPath(id = id, userId = uid)

    private fun makePathWithQuestion(questionId: UUID): OnboardingPath {
        val path = makePath()
        val phase = OnboardingPhase(path = path, position = 0, title = "Phase", description = "Desc")
        val question = PhaseCheckQuestion(
            id = questionId,
            phase = phase,
            position = 0,
            type = CheckQuestionType.SHORT_TEXT,
            question = "What is Kotlin?",
        )
        phase.checkQuestions += question
        path.phases += phase
        return path
    }

    @Nested
    inner class GetOnboardingPathOverviewByUserId {
        @Test
        fun `returns path when user and path exist`() {
            val path = makePath()
            every { userApi.exists(userId) } returns true
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.of(path)

            val result = service.getOnboardingPathByUserId(userId)

            assertEquals(path.id, result.id)
        }

        @Test
        fun `answers a reviewer with the phases' contents, questions included`() {
            // It used to answer with the summary shape: phases and nothing inside them. Reviewer
            // screens rebuilt the rest client-side and could not get the questions at all, so the
            // team page crashed on `phase.questions` the moment questions became phase members.
            val questionId = UUID.randomUUID()
            every { userApi.exists(userId) } returns true
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns
                Optional.of(makePathWithQuestion(questionId))

            val result = service.getOnboardingPathByUserId(userId)

            val phase = result.phases.single()
            assertEquals(1, phase.questions.size)
            assertEquals(questionId, phase.questions.single().id)
            // And the reviewer sees that member's own status, not a blank one.
            assertEquals(QuestionStatus.OPEN, phase.questions.single().status)
        }

        @Test
        fun `throws 404 when user does not exist`() {
            every { userApi.exists(userId) } returns false

            assertThrows<ResponseStatusException> {
                service.getOnboardingPathByUserId(userId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }

        @Test
        fun `throws 404 when path not found for user`() {
            every { userApi.exists(userId) } returns true
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingPathByUserId(userId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetOnboardingPathByAuthId {
        @Test
        fun `returns path for authenticated user`() {
            val path = makePath()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.of(path)

            val result = service.getOnboardingPathForMe(authId)

            assertEquals(path.id, result.id)
        }

        @Test
        fun `throws 404 when authId not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingPathForMe(authId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }

        @Test
        fun `marks passed questions as passed in the returned path`() {
            val questionId = UUID.randomUUID()
            val path = makePathWithQuestion(questionId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.of(path)
            every { questionAttemptRepository.findPassedQuestionIdsByUserId(userId) } returns listOf(questionId)
            every { questionAttemptRepository.findAttemptedQuestionIdsByUserId(userId) } returns listOf(questionId)

            val result = service.getOnboardingPathForMe(authId)

            assertEquals(
                QuestionStatus.PASSED,
                result.phases
                    .first()
                    .questions
                    .first()
                    .status,
            )
        }

        @Test
        fun `marks attempted but never passed questions as retry in the returned path`() {
            val questionId = UUID.randomUUID()
            val path = makePathWithQuestion(questionId)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.of(path)
            every { questionAttemptRepository.findPassedQuestionIdsByUserId(userId) } returns emptyList()
            every { questionAttemptRepository.findAttemptedQuestionIdsByUserId(userId) } returns listOf(questionId)

            val result = service.getOnboardingPathForMe(authId)

            assertEquals(
                QuestionStatus.RETRY,
                result.phases
                    .first()
                    .questions
                    .first()
                    .status,
            )
        }

        @Test
        fun `marks questions without attempts as open in the returned path`() {
            val path = makePathWithQuestion(UUID.randomUUID())
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.of(path)
            every { questionAttemptRepository.findPassedQuestionIdsByUserId(userId) } returns emptyList()
            every { questionAttemptRepository.findAttemptedQuestionIdsByUserId(userId) } returns emptyList()

            val result = service.getOnboardingPathForMe(authId)

            assertEquals(
                QuestionStatus.OPEN,
                result.phases
                    .first()
                    .questions
                    .first()
                    .status,
            )
            verify(exactly = 1) {
                questionAttemptRepository.findPassedQuestionIdsByUserId(userId)
                questionAttemptRepository.findAttemptedQuestionIdsByUserId(userId)
            }
        }

        @Test
        fun `throws 404 when no path for resolved user`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingPathForMe(authId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class DeleteOnboardingPathByUserId {
        @Test
        fun `deletes path when user exists`() {
            every { userApi.exists(userId) } returns true
            every { onboardingPathRepository.deleteByUserId(userId) } just runs

            service.deleteOnboardingPathByUserId(userId)

            verify(exactly = 1) { onboardingPathRepository.deleteByUserId(userId) }
        }

        @Test
        fun `throws 404 when user does not exist`() {
            every { userApi.exists(userId) } returns false

            assertThrows<ResponseStatusException> {
                service.deleteOnboardingPathByUserId(userId)
            }.also { assertEquals(404, it.statusCode.value()) }

            verify(exactly = 0) { onboardingPathRepository.deleteByUserId(any()) }
        }
    }

    @Nested
    inner class DeleteOnboardingPathByAuthId {
        @Test
        fun `deletes path for authenticated user`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingPathRepository.deleteByUserId(userId) } just runs

            service.deleteOnboardingPathForMe(authId)

            verify(exactly = 1) { onboardingPathRepository.deleteByUserId(userId) }
        }

        @Test
        fun `throws 404 when authId not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.deleteOnboardingPathForMe(authId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class RequireMayReplacePath {
        private val callerAuthId = "auth|caller"
        private val ownerAuthId = "auth|owner"
        private val projectA = UUID.randomUUID()
        private val projectB = UUID.randomUUID()
        private val blueprintA = UUID.randomUUID()

        private fun givenPathFrom(project: UUID?) {
            val blueprintId = project?.let { blueprintA }
            every { onboardingPathRepository.findByUserId(userId) } returns
                Optional.of(OnboardingPath(id = pathId, userId = userId, blueprintId = blueprintId))
            if (blueprintId != null) {
                every { blueprintPathRepository.findById(blueprintId) } returns
                    Optional.of(mockk<BlueprintPath> { every { projectId } returns project })
            }
        }

        private fun givenOwnerAssignedTo(vararg projects: UUID) {
            every { userApi.getAuthIdByUserId(userId) } returns Optional.of(ownerAuthId)
            every { userApi.getOnboardingProfileByAuthId(ownerAuthId) } returns Optional.of(
                UserOnboardingProfile(id = userId, projectIds = projects.toSet(), projectRoles = emptyMap()),
            )
        }

        private fun assertForbidden(block: () -> Unit) {
            assertThrows<ResponseStatusException>(block).also { assertEquals(403, it.statusCode.value()) }
        }

        @Test
        fun `lets anybody build a first path`() {
            every { onboardingPathRepository.findByUserId(userId) } returns Optional.empty()

            service.requireMayReplacePath(callerAuthId, userId, projectA)
            service.requireMayReplacePath(callerAuthId, userId, null)
        }

        @Test
        fun `refuses a rebuild by a caller who does not manage the target project`() {
            givenPathFrom(projectA)
            every { userApi.canManageProject(callerAuthId, projectA) } returns false

            assertForbidden { service.requireMayReplacePath(callerAuthId, userId, projectA) }
        }

        @Test
        fun `refuses a rebuild for an owner who is not assigned to the target project`() {
            givenPathFrom(projectA)
            givenOwnerAssignedTo(projectA)
            every { userApi.canManageProject(callerAuthId, projectB) } returns true

            assertForbidden { service.requireMayReplacePath(callerAuthId, userId, projectB) }
        }

        @Test
        fun `lets the target project's manager replace a path from another project`() {
            // A member with a path from A joins B: B's manager must be able to move them, or
            // only an admin could.
            givenPathFrom(projectA)
            givenOwnerAssignedTo(projectA, projectB)
            every { userApi.canManageProject(callerAuthId, projectB) } returns true

            service.requireMayReplacePath(callerAuthId, userId, projectB)

            verify(exactly = 0) { userApi.canManageProject(callerAuthId, projectA) }
        }

        @Test
        fun `lets a rebuild replace a path whose origin is unknown`() {
            givenPathFrom(null)
            givenOwnerAssignedTo(projectA)
            every { userApi.canManageProject(callerAuthId, projectA) } returns true

            service.requireMayReplacePath(callerAuthId, userId, projectA)
        }

        @Test
        fun `lets the origin project's manager delete the path`() {
            givenPathFrom(projectA)
            every { userApi.canManageProject(callerAuthId, projectA) } returns true

            service.requireMayReplacePath(callerAuthId, userId, null)
        }

        @Test
        fun `refuses deleting a path from a project the caller does not manage`() {
            givenPathFrom(projectA)
            every { userApi.canManageProject(callerAuthId, projectA) } returns false

            assertForbidden { service.requireMayReplacePath(callerAuthId, userId, null) }
        }

        @Test
        fun `leaves deleting a path of unknown origin to admins`() {
            givenPathFrom(null)
            every { userApi.isAdmin(callerAuthId) } returns false

            assertForbidden { service.requireMayReplacePath(callerAuthId, userId, null) }

            every { userApi.isAdmin(callerAuthId) } returns true
            service.requireMayReplacePath(callerAuthId, userId, null)
        }

        @Test
        fun `treats a path whose blueprint was deleted as of unknown origin`() {
            every { onboardingPathRepository.findByUserId(userId) } returns
                Optional.of(OnboardingPath(id = pathId, userId = userId, blueprintId = blueprintA))
            every { blueprintPathRepository.findById(blueprintA) } returns Optional.empty()
            every { userApi.isAdmin(callerAuthId) } returns false

            assertForbidden { service.requireMayReplacePath(callerAuthId, userId, null) }
        }
    }

    @Nested
    inner class FindHiddenPhaseForUserId {
        /**
         * A phase generation left empty is hidden from `phases` and only reported as an issue -- but it
         * is what the buddy's add_path_step repairs, so it has to be findable by id, through the
         * owner's own path.
         */
        @Test
        fun `a phase generation left empty is hidden from the hire's path but found for repair`() {
            val path = makePath()
            val empty = OnboardingPhase(
                path = path,
                position = 1,
                title = "Deployment",
                description = "How a change reaches production",
                generationStatus = GenerationStatus.SKIPPED,
            )
            path.phases += empty
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.of(path)
            every { questionAttemptRepository.findPassedQuestionIdsByUserId(userId) } returns emptyList()
            every { questionAttemptRepository.findAttemptedQuestionIdsByUserId(userId) } returns emptyList()

            val read = service.findPathForUserId(userId)!!
            val repairable = service.findHiddenPhaseForUserId(userId, empty.id)

            assertTrue(read.phases.none { it.id == empty.id })
            assertEquals(empty.id, read.generationIssues.single().phaseId)
            assertEquals("How a change reaches production", read.generationIssues.single().description)
            assertEquals(empty.id, repairable?.id)
        }

        @Test
        fun `a visible phase or one from another path is not a hidden phase of theirs`() {
            val path = makePath()
            val visible = OnboardingPhase(path = path, position = 0, title = "Setup", description = "")
            path.phases += visible
            every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.of(path)

            assertNull(service.findHiddenPhaseForUserId(userId, visible.id))
            assertNull(service.findHiddenPhaseForUserId(userId, UUID.randomUUID()))
        }
    }

    @Nested
    inner class FindPathOrigin {
        private val blueprintId = UUID.randomUUID()
        private val projectId = UUID.randomUUID()

        @Test
        fun `names the project whose blueprint the path was built from`() {
            every { onboardingPathRepository.findByUserId(userId) } returns
                Optional.of(OnboardingPath(id = pathId, userId = userId, blueprintId = blueprintId))
            every { blueprintPathRepository.findById(blueprintId) } returns
                Optional.of(mockk<BlueprintPath> { every { projectId } returns this@FindPathOrigin.projectId })

            assertEquals(OnboardingPathService.PathOrigin(projectId), service.findPathOrigin(userId))
        }

        @Test
        fun `has no project for a path without a blueprint`() {
            every { onboardingPathRepository.findByUserId(userId) } returns
                Optional.of(OnboardingPath(id = pathId, userId = userId, blueprintId = null))

            assertEquals(OnboardingPathService.PathOrigin(null), service.findPathOrigin(userId))
        }

        @Test
        fun `is null without a path`() {
            every { onboardingPathRepository.findByUserId(userId) } returns Optional.empty()

            assertNull(service.findPathOrigin(userId))
        }
    }

    @Nested
    inner class GetTeamOverview {
        private val pageable = PageRequest.of(0, 10)

        @Test
        fun `returns empty page when no users match search`() {
            every { userApi.searchUsers("nonexistent", null, null, Pageable.unpaged()) } returns PageImpl(emptyList())

            val result = service.getTeamOverview("nonexistent", null, null, "HIGHEST_PROGRESS", pageable)

            assertTrue(result.isEmpty)
        }

        @Test
        fun `sorts users by highest progress`() {
            val user1Id = UUID.randomUUID()
            val user2Id = UUID.randomUUID()

            val user1 = UserDto(user1Id, "u1", "f1", "l1", null, null, emptySet(), emptyList(), emptyList())
            val user2 = UserDto(user2Id, "u2", "f2", "l2", null, null, emptySet(), emptyList(), emptyList())

            every { userApi.searchUsers(null, null, null, Pageable.unpaged()) } returns PageImpl(listOf(user1, user2))
            every { onboardingPathRepository.findByUserIdIn(listOf(user1Id, user2Id)) } returns listOf(
                // Empty path for user1 (0% progress)
                OnboardingPath(userId = user1Id),
                // User 2 has higher progress (but we just test that it falls back to 0 without phases
                // and sorts by name if both are 0)
                OnboardingPath(userId = user2Id),
            )

            val result = service.getTeamOverview(null, null, null, "HIGHEST_PROGRESS", pageable)

            assertEquals(2, result.content.size)
            // They both have 0 progress, so it should sort by lastname then firstname
            assertEquals("f1", result.content[0].firstname)
            assertEquals("f2", result.content[1].firstname)
        }
    }
}
