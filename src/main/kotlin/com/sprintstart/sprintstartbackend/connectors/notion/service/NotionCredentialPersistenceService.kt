package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionCredentialResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionCredential
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionCredentialId
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionCredentialAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionCredentialStillInUseException
import com.sprintstart.sprintstartbackend.connectors.notion.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionCredentialRepository
import com.sprintstart.sprintstartbackend.connectors.notion.repository.NotionPageConnectionRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
internal class NotionCredentialPersistenceService(
    private val credentialRepository: NotionCredentialRepository,
    private val connectionRepository: NotionPageConnectionRepository,
) {
    @Transactional
    fun persistNew(authId: String, name: String, token: String): NotionCredentialResponse {
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
                )
            )
        } catch (@Suppress("SwallowedException") exception: DataIntegrityViolationException) {
            throw NotionCredentialAlreadyExistsException(name)
        }
        return savedCredential.toResponse()
    }

    @Transactional(readOnly = true)
    fun findAll(authId: String): List<NotionCredentialResponse> {
        return credentialRepository.findAllByIdAuthIdOrderByCreatedAtAsc(authId).map {
            NotionCredentialResponse(
                name = it.id.name,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
            )
        }
    }

    @Transactional
    fun replaceToken(authId: String, name: String, newToken: String): NotionCredentialResponse {
        val credentialId = NotionCredentialId(
            authId = authId,
            name = name,
        )
        val credential = credentialRepository
            .findById(credentialId)
            .orElseThrow {
                NotionCredentialNotFoundException(name)
            }
        credential.token = newToken

        return credentialRepository
            .saveAndFlush(credential)
            .toResponse()
    }

    @Transactional
    fun rename(authId: String, oldName: String, newName: String): NotionCredentialResponse {
        val oldId = NotionCredentialId(
            authId = authId,
            name = oldName,
        )
        val newId = NotionCredentialId(
            authId = authId,
            name = newName,
        )
        val oldCredential = credentialRepository
            .findById(oldId)
            .orElseThrow {
                NotionCredentialNotFoundException(oldName)
            }
        if (oldName == newName) {
            return oldCredential.toResponse()
        }
        if (credentialRepository.existsById(newId)) {
            throw NotionCredentialAlreadyExistsException(newName)
        }
        val newCredential = try {
            credentialRepository.saveAndFlush(
                NotionCredential(
                    id = newId,
                    token = oldCredential.token,
                    createdAt = oldCredential.createdAt,
                )
            )
        } catch (@Suppress("SwallowedException") exception: DataIntegrityViolationException) {
            throw NotionCredentialAlreadyExistsException(newName)
        }

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
            }
            .token
    }
}
