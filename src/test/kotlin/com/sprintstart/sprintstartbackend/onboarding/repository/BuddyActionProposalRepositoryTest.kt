package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyActionProposal
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The single-use guarantee on a confirm is one conditional update. A mocked repository can only say
 * what the service does with its answer; whether the second update really matches nothing has to meet
 * a real database.
 */
@ActiveProfiles("test")
@DataJpaTest
// A JPA slice loads no @Configuration of its own, but the entity graph reaches an
// AttributeConverter that needs the encryptor.
@Import(CryptoConfiguration::class)
class BuddyActionProposalRepositoryTest {
    @Autowired
    private lateinit var repository: BuddyActionProposalRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private val now: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    private fun proposal(userId: UUID = UUID.randomUUID()) =
        BuddyActionProposal(
            userId = userId,
            projectId = UUID.randomUUID(),
            action = "dismiss_escalation",
            params = "{}",
            label = "Dismiss",
            preview = "The question disappears from the inbox.",
            risk = BuddyProposalRisk.DESTRUCTIVE,
            createdAt = now,
            expiresAt = now.plusSeconds(3600),
        )

    private fun claim(id: UUID): Int =
        repository.transition(id, BuddyProposalStatus.PROPOSED, BuddyProposalStatus.CONFIRMING, now)

    @Test
    fun `a transition applies once, and a second one from the same state changes nothing`() {
        val stored = repository.saveAndFlush(proposal())

        val first = claim(stored.id)
        val second = claim(stored.id)

        assertThat(first).isEqualTo(1)
        assertThat(second).isEqualTo(0)
        entityManager.clear()
        val reloaded = repository.findById(stored.id).orElseThrow()
        assertThat(reloaded.status).isEqualTo(BuddyProposalStatus.CONFIRMING)
        assertThat(reloaded.decidedAt).isEqualTo(now)
        assertThat(reloaded.version).isEqualTo(stored.version + 1)
    }

    @Test
    fun `a dismissed proposal can no longer be claimed for confirming`() {
        val stored = repository.saveAndFlush(proposal())
        repository.transition(stored.id, BuddyProposalStatus.PROPOSED, BuddyProposalStatus.DISMISSED, now)

        val claimed = claim(stored.id)

        assertThat(claimed).isEqualTo(0)
    }

    @Test
    fun `finishing records the outcome only for a proposal that is still confirming`() {
        val confirming = repository.saveAndFlush(proposal())
        claim(confirming.id)
        val dismissed = repository.saveAndFlush(proposal())
        repository.transition(dismissed.id, BuddyProposalStatus.PROPOSED, BuddyProposalStatus.DISMISSED, now)

        val recorded = finish(confirming.id)
        val ignored = finish(dismissed.id)

        assertThat(recorded).isEqualTo(1)
        assertThat(ignored).isEqualTo(0)
        entityManager.clear()
        val done = repository.findById(confirming.id).orElseThrow()
        assertThat(done.status).isEqualTo(BuddyProposalStatus.CONFIRMED)
        assertThat(done.resultMessage).isEqualTo("Dismissed the question.")
        assertThat(repository.findById(dismissed.id).orElseThrow().status).isEqualTo(BuddyProposalStatus.DISMISSED)
    }

    private fun finish(id: UUID): Int =
        repository.finish(
            id,
            BuddyProposalStatus.CONFIRMING,
            BuddyProposalStatus.CONFIRMED,
            now,
            "Dismissed the question.",
        )

    @Test
    fun `deleting one user's proposals leaves everybody else's`() {
        val leaving = UUID.randomUUID()
        repository.saveAndFlush(proposal(userId = leaving))
        val staying = repository.saveAndFlush(proposal())

        repository.deleteAllByUserId(leaving)
        entityManager.flush()
        entityManager.clear()

        assertThat(repository.findAll().map { it.id }).containsExactly(staying.id)
    }
}
