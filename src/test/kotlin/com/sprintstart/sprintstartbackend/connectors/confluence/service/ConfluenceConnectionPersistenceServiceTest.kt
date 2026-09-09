package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceSpaceConnection
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.confluence.repository.ConfluenceSpaceConnectionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

class ConfluenceConnectionPersistenceServiceTest {
    private val connectionRepository = mockk<ConfluenceSpaceConnectionRepository>()
    private val service = ConfluenceConnectionPersistenceService(connectionRepository)
    private val projectId = UUID.randomUUID()

    @Test
    fun `stores a validated connection`() {
        val connection = connection()
        every { connectionRepository.existsByProjectIdAndBaseUrlAndSpaceId(any(), any(), any()) } returns false
        every { connectionRepository.saveAndFlush(connection) } returns connection

        val response = service.persist(connection)

        assertThat(response.id).isEqualTo(connection.id)
        assertThat(response.projectId).isEqualTo(projectId)
    }

    @Test
    fun `duplicate discovered inside the transaction is rejected`() {
        val connection = connection()
        every {
            connectionRepository.existsByProjectIdAndBaseUrlAndSpaceId(
                projectId,
                "https://tenant.atlassian.net",
                "123",
            )
        } returns true

        assertThatThrownBy { service.persist(connection) }
            .isInstanceOf(ConfluenceConnectionAlreadyExistsException::class.java)

        verify(exactly = 0) { connectionRepository.saveAndFlush(any()) }
    }

    @Test
    fun `concurrent insert losing the unique constraint is reported as a duplicate`() {
        val connection = connection()
        every { connectionRepository.existsByProjectIdAndBaseUrlAndSpaceId(any(), any(), any()) } returns false
        every { connectionRepository.saveAndFlush(connection) } throws DataIntegrityViolationException("duplicate")

        assertThatThrownBy { service.persist(connection) }
            .isInstanceOf(ConfluenceConnectionAlreadyExistsException::class.java)
    }

    /**
     * Guards the reason this bean exists: the duplicate check and the insert must share a real transaction, which
     * the suspending caller cannot provide.
     */
    @Test
    fun `duplicate check and insert run inside one transaction`() {
        val persist = ConfluenceConnectionPersistenceService::class.java.declaredMethods
            .single { method -> method.name == "persist" }

        assertThat(persist.getAnnotation(Transactional::class.java)).isNotNull
    }

    private fun connection() = ConfluenceSpaceConnection(
        projectId = projectId,
        baseUrl = "https://tenant.atlassian.net",
        spaceId = "123",
        spaceKey = "ENG",
        credentialAuthId = "auth-subject",
        credentialName = "team-token",
    )
}
