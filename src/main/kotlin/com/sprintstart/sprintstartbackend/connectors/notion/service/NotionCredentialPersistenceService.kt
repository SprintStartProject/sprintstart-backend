package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionTokenIdentity
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionCredentialResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionCredential
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionCredentialId
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionWorkspaceConnection
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionCredentialAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionCredentialStillInUseException
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionWorkspaceConnectionConfigurationException
import com.sprintstart.sprintstartbackend.connectors.notion.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionCredentialRepository
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionWorkspaceConnectionRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Owns transactional persistence of encrypted Notion credentials and their connection references.
 *
 * Credential names are scoped per authenticated user. Renames update every referencing workspace
 * connection atomically, while token replacement preserves the remote owner/workspace identity of
 * credentials that are already connected.
 */
@Service
internal class NotionCredentialPersistenceService(
    private val credentialRepository: NotionCredentialRepository,
    private val connectionRepository: NotionWorkspaceConnectionRepository,
) {
    @Transactional
    fun persistNew(
        authId: String, name: String, token: String, workspace: NotionTokenIdentity,
    ): NotionCredentialResponse {
        val credentialId = NotionCredentialId(
            authId = authId,
            name = name,
        )
        if (credentialRepository.existsById(credentialId)) {
            throw NotionCredentialAlreadyExistsException(name)
        }
        val savedCredential = try {
            credentialRepository.saveAndFlush(
                NotionCredential(
                    id = credentialId,
                    token = token,
                    workspaceId = workspace.workspaceId,
                    workspaceName = workspace.workspaceName,
                    tokenOwnerId = workspace.tokenOwnerId,
                ),
            )
        } catch (@Suppress("SwallowedException") exception: DataIntegrityViolationException) {
            throw NotionCredentialAlreadyExistsException(name)
        }
        return savedCredential.toResponse()
    }

    @Transactional(readOnly = true)
    fun findAll(authId: String): List<NotionCredentialResponse> {
        return credentialRepository.findAllByIdAuthIdOrderByCreatedAtAsc(authId).map { it.toResponse() }
    }

    /** Replaces a token only when its validated remote identity matches existing connections. */
    @Transactional
    fun replaceToken(
        authId: String, name: String, newToken: String, workspace: NotionTokenIdentity,
    ): NotionCredentialResponse {
        val credentialId = NotionCredentialId(
            authId = authId,
            name = name,
        )
        val credential = credentialRepository
            .findById(credentialId)
            .orElseThrow {
                NotionCredentialNotFoundException(name)
            }
        applyWorkspace(credential, workspace)
        credential.token = newToken

        return credentialRepository
            .saveAndFlush(credential)
            .toResponse()
    }

    /** Renames a credential and every workspace connection that references it in one transaction. */
    @Transactional
    fun rename(authId: String, oldName: String, newName: String): NotionCredentialResponse {
        val oldCredential = credentialRepository
            .findById(NotionCredentialId(authId = authId, name = oldName))
            .orElseThrow {
                NotionCredentialNotFoundException(oldName)
            }
        if (oldName == newName) {
            return oldCredential.toResponse()
        }
        val newId = NotionCredentialId(authId = authId, name = newName)
        if (credentialRepository.existsById(newId)) {
            throw NotionCredentialAlreadyExistsException(newName)
        }
        val newCredential = copyCredential(oldCredential, newId)
        val connections = connectionRepository.findAllByCredentialAuthIdAndCredentialName(
            authId = authId,
            credentialName = oldName,
        )
        connections.forEach { it.credentialName = newName }
        connectionRepository.saveAllAndFlush(connections)

        credentialRepository.delete(oldCredential)
        credentialRepository.flush()
        return newCredential.toResponse()
    }

    private fun copyCredential(source: NotionCredential, newId: NotionCredentialId): NotionCredential = try {
        credentialRepository.saveAndFlush(
            NotionCredential(
                id = newId,
                token = source.token,
                workspaceId = source.workspaceId,
                workspaceName = source.workspaceName,
                tokenOwnerId = source.tokenOwnerId,
                createdAt = source.createdAt,
            ),
        )
    } catch (@Suppress("SwallowedException") exception: DataIntegrityViolationException) {
        throw NotionCredentialAlreadyExistsException(newId.name)
    }

    @Transactional
    fun delete(authId: String, name: String) {
        val credentialId = NotionCredentialId(
            authId = authId,
            name = name,
        )
        val credential = credentialRepository
            .findById(credentialId)
            .orElseThrow {
                NotionCredentialNotFoundException(name)
            }
        if (connectionRepository.existsByCredentialAuthIdAndCredentialName(authId, name)) {
            throw NotionCredentialStillInUseException(name)
        }
        credentialRepository.delete(credential)
        credentialRepository.flush()
    }

    @Transactional(readOnly = true)
    fun requireToken(authId: String, name: String): String {
        val credentialId = NotionCredentialId(
            authId = authId,
            name = name,
        )
        return credentialRepository
            .findById(credentialId)
            .orElseThrow {
                NotionCredentialNotFoundException(name)
            }.token
    }

    /** Records newly available identity metadata without discarding previously known workspace data. */
    @Transactional
    fun recordTokenIdentity(authId: String, name: String, workspace: NotionTokenIdentity) {
        val credential = credentialRepository
            .findById(NotionCredentialId(authId, name))
            .orElseThrow { NotionCredentialNotFoundException(name) }
        applyWorkspace(credential, workspace)
    }

    private fun requireSameIdentity(connections: List<NotionWorkspaceConnection>, workspace: NotionTokenIdentity) {
        val changed = connections.any {
            val differentWorkspace = workspace.workspaceId != null &&
                it.workspaceId != null &&
                it.workspaceId != workspace.workspaceId
            val differentOwner = it.tokenOwnerId != null && it.tokenOwnerId != workspace.tokenOwnerId
            differentWorkspace || differentOwner
        }
        if (changed) {
            throw NotionWorkspaceConnectionConfigurationException(
                "A connected credential must keep the same Notion token owner and workspace",
            )
        }
    }

    private fun requireNoDuplicate(connections: List<NotionWorkspaceConnection>, workspace: NotionTokenIdentity) {
        val workspaceId = workspace.workspaceId ?: return
        val duplicate = connections.firstOrNull { connection ->
            connectionRepository.existsByProjectIdAndWorkspaceIdAndTokenOwnerIdAndIdNot(
                connection.projectId,
                workspaceId,
                workspace.tokenOwnerId,
                connection.id,
            )
        }
        if (duplicate != null) {
            throw NotionCredentialAlreadyExistsException(duplicate.credentialName)
        }
    }

    private fun applyWorkspace(credential: NotionCredential, workspace: NotionTokenIdentity) {
        val connections = connectionRepository.findAllByCredentialAuthIdAndCredentialName(
            credential.id.authId,
            credential.id.name,
        )
        requireSameIdentity(connections, workspace)
        requireNoDuplicate(connections, workspace)
        credential.workspaceId = workspace.workspaceId ?: credential.workspaceId
        credential.workspaceName = workspace.workspaceName ?: credential.workspaceName
        credential.tokenOwnerId = workspace.tokenOwnerId
        connections.forEach {
            it.workspaceId = workspace.workspaceId ?: it.workspaceId
            it.workspaceName = workspace.workspaceName ?: it.workspaceName
            it.tokenOwnerId = workspace.tokenOwnerId
        }
    }
}
