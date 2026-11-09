package com.sprintstart.sprintstartbackend.buddy.repository

import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * Naming a session is one conditional update, so two first messages cannot fail each other. Whether
 * the second update really matches nothing, and whether the version still moves, has to meet a real
 * database.
 */
@ActiveProfiles("test")
@DataJpaTest
// A JPA slice loads no @Configuration of its own, but the entity graph reaches an
// AttributeConverter that needs the encryptor.
@Import(CryptoConfiguration::class)
class BuddySessionRepositoryTest {
    @Autowired
    private lateinit var repository: BuddySessionRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private fun stored(title: String = "") = repository.saveAndFlush(
        BuddySession(userId = UUID.randomUUID(), title = title),
    )

    @Test
    fun `the first name sticks and the second changes nothing`() {
        val session = stored()

        val first = repository.nameIfUntitled(session.id, "Setting up the dev box")
        val second = repository.nameIfUntitled(session.id, "Getting started")

        assertThat(first).isEqualTo(1)
        assertThat(second).isEqualTo(0)
        entityManager.clear()
        val reloaded = repository.findById(session.id).orElseThrow()
        assertThat(reloaded.title).isEqualTo("Setting up the dev box")
        assertThat(reloaded.version).isEqualTo(session.version + 1)
    }

    @Test
    fun `a session with a title keeps it`() {
        val session = stored(title = "Deploying on Thursdays")

        assertThat(repository.nameIfUntitled(session.id, "Something else")).isEqualTo(0)
        entityManager.clear()
        assertThat(repository.findById(session.id).orElseThrow().title).isEqualTo("Deploying on Thursdays")
    }

    @Test
    fun `a session whose title is only whitespace counts as untitled`() {
        val session = stored(title = "  ")

        assertThat(repository.nameIfUntitled(session.id, "Setting up the dev box")).isEqualTo(1)
    }

    @Test
    fun `a copy loaded before the name cannot write the empty title back`() {
        val session = stored()
        entityManager.clear()
        val stale = repository.findById(session.id).orElseThrow()
        entityManager.detach(stale)

        repository.nameIfUntitled(session.id, "Setting up the dev box")
        stale.summary = "an older fold"

        assertThrows<ObjectOptimisticLockingFailureException> { repository.saveAndFlush(stale) }
    }
}
