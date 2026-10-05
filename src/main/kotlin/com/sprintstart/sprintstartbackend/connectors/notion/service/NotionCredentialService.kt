package com.sprintstart.sprintstartbackend.connectors.notion.service

import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionClient
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.AddNotionCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.ChangeNotionCredentialNameRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.ChangeNotionCredentialTokenRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.DeleteNotionCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionCredentialResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

/**
 * Validates and manages user-owned Notion personal access token credentials.
 *
 * Remote validation completes before database work begins. The transactional persistence service
 * owns all writes so suspended HTTP calls never cross a thread-bound Spring transaction.
 */
@Service
internal class NotionCredentialService(
    private val notionClient: NotionClient,
    private val persistenceService: NotionCredentialPersistenceService,
) {
    /** Validates a PAT before storing its encrypted value and safe remote identity metadata. */
    suspend fun addCredential(
        authId: String,
        request: AddNotionCredentialRequest,
    ): NotionCredentialResponse {
        val normalizedName = request.name.trim()
        val normalizedToken = request.token.trim()
        val workspace = notionClient.validateConnection(normalizedToken)
        return withContext(Dispatchers.IO) {
            persistenceService.persistNew(
                authId = authId,
                name = normalizedName,
                token = normalizedToken,
                workspace = workspace,
            )
        }
    }

    fun getCredentials(authId: String): List<NotionCredentialResponse> {
        return persistenceService.findAll(authId)
    }

    /** Validates a replacement PAT before atomically updating its stored encrypted value. */
    suspend fun changeToken(
        authId: String,
        request: ChangeNotionCredentialTokenRequest,
    ): NotionCredentialResponse {
        val normalizedName = request.name.trim()
        val normalizedToken = request.newToken.trim()

        val workspace = notionClient.validateConnection(normalizedToken)
        return withContext(Dispatchers.IO) {
            persistenceService.replaceToken(
                authId = authId,
                name = normalizedName,
                newToken = normalizedToken,
                workspace = workspace,
            )
        }
    }

    fun changeName(
        authId: String,
        request: ChangeNotionCredentialNameRequest,
    ): NotionCredentialResponse {
        val normalizedOldName = request.oldName.trim()
        val normalizedNewName = request.newName.trim()
        return persistenceService.rename(
            authId = authId,
            oldName = normalizedOldName,
            newName = normalizedNewName,
        )
    }

    fun deleteCredential(authId: String, request: DeleteNotionCredentialRequest) {
        val normalizedName = request.name.trim()
        persistenceService.delete(
            authId = authId,
            name = normalizedName,
        )
    }
}
