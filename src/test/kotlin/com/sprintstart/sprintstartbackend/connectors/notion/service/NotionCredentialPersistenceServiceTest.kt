package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionTokenIdentity
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionCredential
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionCredentialId
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionCredentialAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionCredentialRepository
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionWorkspaceConnectionRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.util.Optional

internal class NotionCredentialPersistenceServiceTest {
    private val credentialRepository = mockk<NotionCredentialRepository>()
    private val connectionRepository = mockk<NotionWorkspaceConnectionRepository>()
    private val service = NotionCredentialPersistenceService(credentialRepository, connectionRepository)

    @Test
    fun `a new credential that loses the unique constraint race is reported as a duplicate with the cause`() {
        val conflict = DataIntegrityViolationException("duplicate key")
        every { credentialRepository.existsById(NotionCredentialId("auth", "team")) } returns false
        every { credentialRepository.saveAndFlush(any()) } throws conflict

        val thrown = runCatching {
            service.persistNew("auth", "team", "secret-token", NotionTokenIdentity(tokenOwnerId = "owner"))
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(NotionCredentialAlreadyExistsException::class.java)
        assertThat(thrown).hasMessage("Notion credential 'team' already exists")
        assertThat(thrown?.cause).isSameAs(conflict)
    }

    @Test
    fun `a rename that loses the unique constraint race is reported as a duplicate with the cause`() {
        val conflict = DataIntegrityViolationException("duplicate key")
        val old = NotionCredential(id = NotionCredentialId("auth", "old"), token = "secret-token")
        every { credentialRepository.findById(NotionCredentialId("auth", "old")) } returns Optional.of(old)
        every { credentialRepository.existsById(NotionCredentialId("auth", "new")) } returns false
        every { credentialRepository.saveAndFlush(any()) } throws conflict

        val thrown = runCatching { service.rename("auth", "old", "new") }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(NotionCredentialAlreadyExistsException::class.java)
        assertThat(thrown).hasMessage("Notion credential 'new' already exists")
        assertThat(thrown?.cause).isSameAs(conflict)
    }
}
