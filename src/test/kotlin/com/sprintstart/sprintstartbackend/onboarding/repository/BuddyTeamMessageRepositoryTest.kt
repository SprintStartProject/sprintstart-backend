package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyTeamMessage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyTeamSession
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

/**
 * What a team reply opened — and whether it was cut short — has to survive the trip to the database and
 * back: the next message reads the areas to decide which tools to mount, and the incomplete flag is what
 * tells a reader the reply stopped mid-air instead of ending.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import(CryptoConfiguration::class)
class BuddyTeamMessageRepositoryTest {
    @Autowired
    private lateinit var repository: BuddyTeamMessageRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private fun session(): BuddyTeamSession =
        BuddyTeamSession(userId = UUID.randomUUID(), projectId = UUID.randomUUID()).also { entityManager.persist(it) }

    private fun message(session: BuddyTeamSession, offsetSeconds: Long, opened: String?) =
        BuddyTeamMessage(
            session = session,
            role = BuddyMessageRole.ASSISTANT,
            content = "reply $offsetSeconds",
            createdAt = Instant.parse("2026-09-21T10:00:00Z").plusSeconds(offsetSeconds),
            openedAreas = opened,
        )

    @Test
    fun `the areas a reply opened come back exactly as stored, and none comes back as null`() {
        val session = session()
        repository.save(message(session, 1, "ARRIVAL,KNOWLEDGE"))
        repository.save(message(session, 2, null))
        entityManager.flush()
        entityManager.clear()

        val stored = repository.findAllBySessionIdOrderByCreatedAtAsc(session.id)

        assertThat(stored.map { it.openedAreas }).containsExactly("ARRIVAL,KNOWLEDGE", null)
    }

    @Test
    fun `a reply that was cut short comes back marked incomplete`() {
        val session = session()
        repository.save(
            BuddyTeamMessage(
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = "Part of ",
                createdAt = Instant.parse("2026-09-21T10:00:01Z"),
                isIncomplete = true,
            ),
        )
        repository.save(message(session, 2, null))
        entityManager.flush()
        entityManager.clear()

        val stored = repository.findAllBySessionIdOrderByCreatedAtAsc(session.id)

        assertThat(stored.map { it.isIncomplete }).containsExactly(true, false)
    }
}
