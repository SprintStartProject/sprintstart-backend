package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContentProvenance
import com.sprintstart.sprintstartbackend.onboarding.external.model.GraphProposalOutcome
import com.sprintstart.sprintstartbackend.onboarding.external.model.GraphProvenanceSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.ProposedCompetencySchema
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyTombstone
import com.sprintstart.sprintstartbackend.onboarding.model.entity.VocabularyGenerationState
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyTombstoneRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.VocabularyGenerationStateRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.util.Optional
import kotlin.test.assertEquals

/**
 * Generation writes to the vocabulary with nobody reviewing it, so what it must **not** do is the
 * part worth pinning: it may not overwrite a person's row, and it may not resurrect what somebody
 * removed.
 */
class VocabularyGenerationServiceTest {
    private val competencyRepository: CompetencyRepository = mockk(relaxed = true)
    private val tombstoneRepository: CompetencyTombstoneRepository = mockk(relaxed = true)
    private val stateRepository: VocabularyGenerationStateRepository = mockk(relaxed = true)
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val transactionManager: PlatformTransactionManager = mockk()

    private val service = VocabularyGenerationService(
        competencyRepository,
        tombstoneRepository,
        stateRepository,
        onboardingAiClient,
        CompetencyAreaNormalizer(competencyRepository),
        transactionManager,
    )

    init {
        every { transactionManager.getTransaction(any()) } returns SimpleTransactionStatus()
        every { transactionManager.commit(any<TransactionStatus>()) } returns Unit
        every { competencyRepository.save(any()) } answers { firstArg() }
        every { stateRepository.save(any()) } answers { firstArg() }
        every { competencyRepository.findDistinctAreas() } returns emptyList()
        every { tombstoneRepository.findAll() } returns emptyList()
        every { stateRepository.findById(VocabularyGenerationState.SINGLETON_ID) } returns Optional.empty()
    }

    private fun proposal(vararg competencies: ProposedCompetencySchema) = GraphProposalOutcome(
        status = "proposed",
        competencies = competencies.toList(),
        provenance = GraphProvenanceSchema(corpusFingerprint = "fp-2"),
    )

    private fun proposed(key: String, label: String = key, area: String? = null) =
        ProposedCompetencySchema(key = key, label = label, kind = "SKILL", area = area)

    private fun live(key: String, provenance: ContentProvenance) = Competency(
        key = key,
        label = key,
        kind = CompetencyKind.SKILL,
        provenance = provenance,
    )

    @Test
    fun `publishes a proposed competency without anybody approving it`() = runTest {
        every { competencyRepository.findAll() } returns emptyList()
        coEvery { onboardingAiClient.proposeCompetencyGraph(any(), any(), any(), any()) } returns
            proposal(proposed("kotlin", "Kotlin", area = "Backend"))

        val summary = service.generate()

        assertEquals(1, summary.added)
        val saved = slot<Competency>()
        verify { competencyRepository.save(capture(saved)) }
        assertEquals("kotlin", saved.captured.key)
        assertEquals("Backend", saved.captured.area)
        // Stamped AI so a later run may improve it -- and so an edit can take it away from the AI.
        assertEquals(ContentProvenance.AI, saved.captured.provenance)
    }

    @Test
    fun `leaves a competency a person authored exactly as they left it`() = runTest {
        val edited = live("kotlin", ContentProvenance.PM).apply { label = "Kotlin, our way" }
        every { competencyRepository.findAll() } returns listOf(edited)
        coEvery { onboardingAiClient.proposeCompetencyGraph(any(), any(), any(), any()) } returns
            proposal(proposed("kotlin", "Kotlin"))

        val summary = service.generate()

        assertEquals("Kotlin, our way", edited.label)
        assertEquals(ContentProvenance.PM, edited.provenance)
        assertEquals(1, summary.skippedPmRows)
        assertEquals(0, summary.added)
    }

    @Test
    fun `updates a competency the generator itself wrote last time`() = runTest {
        val generated = live("kotlin", ContentProvenance.AI)
        every { competencyRepository.findAll() } returns listOf(generated)
        coEvery { onboardingAiClient.proposeCompetencyGraph(any(), any(), any(), any()) } returns
            proposal(proposed("kotlin", "Kotlin 2.1", area = "Backend"))

        service.generate()

        assertEquals("Kotlin 2.1", generated.label)
        assertEquals("Backend", generated.area)
    }

    /**
     * The generator is told about tombstones and can still propose one anyway. Being told is not
     * being prevented, and this is the layer that owns the database.
     */
    @Test
    fun `refuses to resurrect a competency somebody removed`() = runTest {
        every { competencyRepository.findAll() } returns emptyList()
        every { tombstoneRepository.findAll() } returns
            listOf(CompetencyTombstone(key = "kotlin", label = "Kotlin"))
        coEvery { onboardingAiClient.proposeCompetencyGraph(any(), any(), any(), any()) } returns
            proposal(proposed("kotlin", "Kotlin"))

        val summary = service.generate()

        assertEquals(0, summary.added)
        verify(exactly = 0) { competencyRepository.save(any()) }
    }

    @Test
    fun `an unchanged corpus writes nothing at all`() = runTest {
        every { competencyRepository.findAll() } returns emptyList()
        coEvery { onboardingAiClient.proposeCompetencyGraph(any(), any(), any(), any()) } returns
            GraphProposalOutcome(status = "unchanged", notes = listOf("corpus unchanged"))

        val summary = service.generate()

        assertEquals("unchanged", summary.status)
        verify(exactly = 0) { competencyRepository.save(any()) }
        // Not even the fingerprint: there is nothing new to remember.
        verify(exactly = 0) { stateRepository.save(any()) }
    }

    @Test
    fun `remembers the fingerprint so the next crawl can be told nothing changed`() = runTest {
        every { competencyRepository.findAll() } returns emptyList()
        coEvery { onboardingAiClient.proposeCompetencyGraph(any(), any(), any(), any()) } returns
            proposal(proposed("kotlin"))

        service.generate()

        val state = slot<VocabularyGenerationState>()
        verify { stateRepository.save(capture(state)) }
        assertEquals("fp-2", state.captured.lastFingerprint)
    }
}
