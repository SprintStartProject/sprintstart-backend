package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.model.BlueprintOutcome
import com.sprintstart.sprintstartbackend.onboarding.external.model.BlueprintProvenanceSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.BlueprintSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.GeneratedBlueprint
import com.sprintstart.sprintstartbackend.onboarding.external.model.GeneratedBlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Blueprint
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.repository.BlueprintRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import com.sprintstart.sprintstartbackend.onboarding.external.model.GenerateBlueprintsResponse as AiGenerateBlueprintsResponse

class BlueprintServiceTest {
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val blueprintRepository: BlueprintRepository = mockk()
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)
    private val service =
        BlueprintService(onboardingAiClient, blueprintRepository, transactionManager)

    private fun makeBlueprint(scope: String, version: String, status: BlueprintStatus): Blueprint =
        Blueprint(scope = scope, version = version, status = status)

    private fun makeStep(blueprint: Blueprint, stepId: String, title: String, pos: Int): BlueprintStep =
        BlueprintStep(blueprint = blueprint, stepId = stepId, title = title, position = pos)

    @Nested
    inner class GenerateBlueprints {
        @Test
        fun `maps generated blueprint to ACTIVE and archives previous ACTIVE`() = runTest {
            val currentActive = makeBlueprint("global", "1", BlueprintStatus.ACTIVE)
            val aiStep = GeneratedBlueprintStep(id = "step-1", title = "Setup")
            val aiBlueprint = GeneratedBlueprint(scope = "global", version = "2", steps = listOf(aiStep))
            val outcome = BlueprintOutcome(scope = "global", status = "updated", blueprint = aiBlueprint)
            coEvery { onboardingAiClient.generateBlueprints(any(), any()) } returns AiGenerateBlueprintsResponse(
                outcomes = listOf(outcome),
            )
            every { blueprintRepository.findByScopeAndStatus("global", BlueprintStatus.ACTIVE) } returns currentActive
            val savedSlot = slot<Blueprint>()
            every { blueprintRepository.save(capture(savedSlot)) } returns
                makeBlueprint("global", "2", BlueprintStatus.ACTIVE)

            val result = service.generateBlueprints(listOf("global"))

            assertEquals(BlueprintStatus.ARCHIVED, currentActive.status)
            assertEquals(BlueprintStatus.ACTIVE, savedSlot.captured.status)
            assertEquals("2", savedSlot.captured.version)
            assertEquals(1, result.outcomes.size)
            assertEquals("global", result.outcomes[0].scope)
            assertEquals("updated", result.outcomes[0].status)
        }

        @Test
        fun `creates ACTIVE when no previous ACTIVE exists`() = runTest {
            val aiStep = GeneratedBlueprintStep(id = "step-1", title = "Setup")
            val aiBlueprint = GeneratedBlueprint(scope = "global", version = "1", steps = listOf(aiStep))
            val outcome = BlueprintOutcome(scope = "global", status = "created", blueprint = aiBlueprint)
            coEvery { onboardingAiClient.generateBlueprints(any(), any()) } returns AiGenerateBlueprintsResponse(
                outcomes = listOf(outcome),
            )
            every { blueprintRepository.findByScopeAndStatus("global", BlueprintStatus.ACTIVE) } returns null
            every { blueprintRepository.save(any()) } returns makeBlueprint("global", "1", BlueprintStatus.ACTIVE)

            val result = service.generateBlueprints(listOf("global"))

            verify(exactly = 1) { blueprintRepository.save(any()) }
            assertEquals(1, result.outcomes.size)
            assertEquals("global", result.outcomes[0].scope)
            assertEquals("created", result.outcomes[0].status)
        }

        @Test
        fun `skips outcomes where blueprint is null`() = runTest {
            val outcome = BlueprintOutcome(scope = "global", status = "unchanged", blueprint = null)
            coEvery { onboardingAiClient.generateBlueprints(any(), any()) } returns AiGenerateBlueprintsResponse(
                outcomes = listOf(outcome),
            )
            every { blueprintRepository.findByScopeAndStatus("global", BlueprintStatus.ACTIVE) } returns null

            service.generateBlueprints(listOf("global"))

            verify(exactly = 0) { blueprintRepository.save(any()) }
        }

        @Test
        fun `does not activate escalated blueprints`() = runTest {
            val aiStep = GeneratedBlueprintStep(id = "step-1", title = "Setup")
            val aiBlueprint = GeneratedBlueprint(scope = "global", version = "2", steps = listOf(aiStep))
            val outcome = BlueprintOutcome(scope = "global", status = "escalated", blueprint = aiBlueprint)
            coEvery { onboardingAiClient.generateBlueprints(any(), any()) } returns AiGenerateBlueprintsResponse(
                outcomes = listOf(outcome),
            )
            every { blueprintRepository.findByScopeAndStatus("global", BlueprintStatus.ACTIVE) } returns null

            val result = service.generateBlueprints(listOf("global"))

            verify(exactly = 0) { blueprintRepository.save(any()) }
            assertEquals("escalated", result.outcomes[0].status)
        }

        @Test
        fun `sends the stored corpus fingerprint to the AI and persists the new one`() = runTest {
            val currentActive = Blueprint(
                scope = "global",
                version = "1",
                status = BlueprintStatus.ACTIVE,
                corpusFingerprint = "old-fp",
            )
            val activeSlot = slot<List<BlueprintSchema>>()
            val aiBlueprint = GeneratedBlueprint(
                scope = "global",
                version = "2",
                steps = listOf(GeneratedBlueprintStep(id = "step-1", title = "Setup")),
                provenance = BlueprintProvenanceSchema(corpusFingerprint = "new-fp"),
            )
            val outcome = BlueprintOutcome(scope = "global", status = "updated", blueprint = aiBlueprint)
            coEvery { onboardingAiClient.generateBlueprints(any(), capture(activeSlot)) } returns
                AiGenerateBlueprintsResponse(outcomes = listOf(outcome))
            every { blueprintRepository.findByScopeAndStatus("global", BlueprintStatus.ACTIVE) } returns currentActive
            val savedSlot = slot<Blueprint>()
            every { blueprintRepository.save(capture(savedSlot)) } returns currentActive

            service.generateBlueprints(listOf("global"))

            assertEquals("old-fp", activeSlot.captured[0].provenance?.corpusFingerprint)
            assertEquals("new-fp", savedSlot.captured.corpusFingerprint)
        }
    }

    @Nested
    inner class ListVersions {
        @Test
        fun `returns only ARCHIVED blueprint versions for the scope`() {
            val archived = makeBlueprint("global", "1", BlueprintStatus.ARCHIVED)
            every { blueprintRepository.findAllByScopeAndStatus("global", BlueprintStatus.ARCHIVED) } returns
                listOf(archived)

            val result = service.listVersions("global")

            assertEquals(listOf("1"), result.versions)
            assertEquals("global", result.scope)
        }
    }

    @Nested
    inner class Rollback {
        @Test
        fun `creates a new ACTIVE from the archived version and archives the current ACTIVE`() {
            val archivedBlueprint = makeBlueprint("global", "1", BlueprintStatus.ARCHIVED)
            archivedBlueprint.steps.add(makeStep(archivedBlueprint, "step-1", "Setup", 0))
            archivedBlueprint.steps.add(makeStep(archivedBlueprint, "step-2", "Configure", 1))
            val currentActive = makeBlueprint("global", "2", BlueprintStatus.ACTIVE)
            every {
                blueprintRepository.findByScopeAndStatusAndVersion("global", BlueprintStatus.ARCHIVED, "1")
            } returns archivedBlueprint
            every { blueprintRepository.findByScopeAndStatus("global", BlueprintStatus.ACTIVE) } returns currentActive
            every { blueprintRepository.delete(any()) } just Runs
            val savedSlot = slot<Blueprint>()
            every { blueprintRepository.save(capture(savedSlot)) } returns
                makeBlueprint("global", "1", BlueprintStatus.ACTIVE)

            service.rollback("global", "1")

            assertEquals(BlueprintStatus.ARCHIVED, currentActive.status)
            assertEquals(BlueprintStatus.ACTIVE, savedSlot.captured.status)
            assertEquals("1", savedSlot.captured.version)
            verify(exactly = 1) { blueprintRepository.delete(archivedBlueprint) }
        }

        @Test
        fun `throws 404 when the requested version does not exist`() {
            every {
                blueprintRepository.findByScopeAndStatusAndVersion("global", BlueprintStatus.ARCHIVED, "99")
            } returns null

            val ex = assertThrows<ResponseStatusException> { service.rollback("global", "99") }

            assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
        }
    }
}
