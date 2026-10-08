package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionTokenIdentity
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionCredential
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionCredentialId
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionWorkspaceConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionWorkspaceConnectionConfigurationException
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionCredentialRepository
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionWorkspaceConnectionRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class NotionCredentialWorkspaceTest {
    private val credentials = mockk<NotionCredentialRepository>()
    private val connections = mockk<NotionWorkspaceConnectionRepository>()
    private val service = NotionCredentialPersistenceService(credentials, connections)
    private val identity = NotionTokenIdentity("bot", "workspace", "Team")
    private val credential = NotionCredential(NotionCredentialId("auth", "name"), "secret")

    @Test
    fun `new credential persists workspace identity and does not return its token`() {
        every { credentials.existsById(any()) } returns false
        every { credentials.saveAndFlush(any()) } answers {
            firstArg<NotionCredential>().also {
                assertThat(it.workspaceId).isEqualTo("workspace")
                assertThat(it.tokenOwnerId).isEqualTo("bot")
            }
        }
        val result = service.persistNew("auth", "name", "secret", identity)
        assertThat(result.workspaceId).isEqualTo("workspace")
        assertThat(result.workspaceName).isEqualTo("Team")
        assertThat(result.toString()).doesNotContain("secret")
    }

    @Test
    fun `PAT credential is valid without bot workspace metadata`() {
        every { credentials.existsById(any()) } returns false
        every { credentials.saveAndFlush(any()) } answers { firstArg() }

        val result = service.persistNew("auth", "pat", "secret", NotionTokenIdentity("user-id"))

        assertThat(result.workspaceId).isNull()
        assertThat(result.workspaceName).isNull()
    }

    @Test
    fun `connected token cannot silently switch token owner`() {
        val connection = NotionWorkspaceConnection(
            projectId = UUID.randomUUID(),
            credentialAuthId = "auth",
            credentialName = "name",
            workspaceId = "workspace",
            workspaceName = "Team",
            tokenOwnerId = "bot",
        )
        every { credentials.findById(credential.id) } returns Optional.of(credential)
        every { connections.findAllByCredentialAuthIdAndCredentialName("auth", "name") } returns listOf(connection)

        assertThrows<NotionWorkspaceConnectionConfigurationException> {
            service.replaceToken("auth", "name", "replacement", identity.copy(tokenOwnerId = "another-user"))
        }
        assertThat(credential.token).isEqualTo("secret")
        assertThat(connection.tokenOwnerId).isEqualTo("bot")
    }

    @Test
    fun `legacy credential hydrates its workspace on first validation`() {
        val connection = NotionWorkspaceConnection(
            projectId = UUID.randomUUID(),
            credentialAuthId = "auth",
            credentialName = "name",
        )
        every { credentials.findById(credential.id) } returns Optional.of(credential)
        every { connections.findAllByCredentialAuthIdAndCredentialName("auth", "name") } returns listOf(connection)

        every {
            connections.existsByProjectIdAndWorkspaceIdAndTokenOwnerIdAndIdNot(any(), any(), any(), any())
        } returns false

        service.recordTokenIdentity("auth", "name", identity)

        assertThat(credential.workspaceId).isEqualTo("workspace")
        assertThat(connection.tokenOwnerId).isEqualTo("bot")
        assertThat(connection.workspaceName).isEqualTo("Team")
    }
}
