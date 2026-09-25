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

@Service
internal class NotionCredentialService(
    private val notionClient: NotionClient,
    private val persistenceService: NotionCredentialPersistenceService,
) {
    suspend fun addCredential(
        authId: String,
        request: AddNotionCredentialRequest,
    ): NotionCredentialResponse {
        val normalizedName = request.name.trim()
        val normalizedToken = request.token.trim()
        notionClient.validateConnection(normalizedToken)
        return withContext(Dispatchers.IO) {
            persistenceService.persistNew(
                authId = authId,
                name = normalizedName,
                token = normalizedToken,
            )
        }
    }

    fun getCredentials(authId: String): List<NotionCredentialResponse> {
        return persistenceService.findAll(authId)
    }

    suspend fun changeToken(
        authId: String,
        request: ChangeNotionCredentialTokenRequest,
    ): NotionCredentialResponse {
        val normalizedName = request.name.trim()
        val normalizedToken = request.newToken.trim()

        notionClient.validateConnection(normalizedToken)
        return withContext(Dispatchers.IO) {
            persistenceService.replaceToken(
                authId = authId,
                name = normalizedName,
                newToken = normalizedToken,
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
