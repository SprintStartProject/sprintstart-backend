package com.sprintstart.sprintstartbackend.connectors.atlassian.service

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialSecret
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.entity.AtlassianCredentialId
import com.sprintstart.sprintstartbackend.connectors.atlassian.repository.AtlassianCredentialRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Resolves decrypted Atlassian credentials for other connector modules (Jira, Confluence). */
@Service
internal class AtlassianCredentialRuntimeService(
    private val credentialRepository: AtlassianCredentialRepository,
) : AtlassianCredentialApi {
    @Transactional(readOnly = true)
    override fun findSecret(authId: String, credentialName: String): AtlassianCredentialSecret? {
        val credential = credentialRepository
            .findById(AtlassianCredentialId(authId, credentialName))
            .orElse(null)
            ?: return null
        return AtlassianCredentialSecret(userEmail = credential.userEmail, apiToken = credential.authToken)
    }
}
