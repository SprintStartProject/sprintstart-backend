package com.sprintstart.sprintstartbackend.connectors.atlassian.service

import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.AddAtlassianCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.ChangeAtlassianCredentialNameRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.ChangeAtlassianCredentialTokenRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.DeleteAtlassianCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.response.AtlassianCredentialDto
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.response.toDto
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.entity.AtlassianCredential
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.entity.AtlassianCredentialId
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.atlassian.repository.AtlassianCredentialRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
internal class AtlassianCredentialService(
    private val credentialsRepository: AtlassianCredentialRepository,
) {
    /**
     * Adds a new Atlassian credential to the repository if it does not already exist.
     *
     * @param request The request object containing the details of the credential to add, including
     * the user's email, the name of the token, and the authentication token.
     * @throws AtlassianCredentialAlreadyExistsException If a credential with the specified user email
     * and token name already exists.
     */
    @Tracked("Storing a new Atlassian credential")
    fun addCredentials(authId: String, request: AddAtlassianCredentialRequest) {
        if (credentialsRepository.existsById(AtlassianCredentialId(authId, request.tokenName))) {
            throw AtlassianCredentialAlreadyExistsException(request.userEmail, request.tokenName)
        }

        val credentials = AtlassianCredential(
            id = AtlassianCredentialId(authId, request.tokenName),
            authToken = request.authToken,
            userEmail = request.userEmail,
        )
        credentialsRepository.save(credentials)
    }

    /**
     * Retrieves all Atlassian credentials associated with a specific user based on their email.
     *
     * @param authId The auth subject of the user whose Atlassian credentials are to be retrieved.
     * @return A list of AtlassianCredentialDto containing the user's credential details.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving Atlassian credentials of a user")
    fun getCredentialsOfUser(authId: String): List<AtlassianCredentialDto> =
        credentialsRepository.findAllByAuthId(authId).map { it.toDto() }

    /**
     * Removes an Atlassian credential associated with the specified user email and token name.
     * If no matching credential is found, an AtlassianCredentialNotFoundException is thrown.
     *
     * @param request The request object containing the details of the credential to be removed,
     * including the user's email and the token name.
     * @throws AtlassianCredentialNotFoundException If no credential matches the provided email and token name.
     */
    @Tracked("Removing Atlassian credentials")
    fun removeCredential(authId: String, request: DeleteAtlassianCredentialRequest) {
        if (!credentialsRepository.existsById(AtlassianCredentialId(authId, request.tokenName))) {
            throw AtlassianCredentialNotFoundException(request.userEmail, request.tokenName)
        }

        credentialsRepository.deleteById(AtlassianCredentialId(authId, request.tokenName))
    }

    /**
     * Updates the name of an Atlassian credential associated with the specified user email and current name.
     * If no matching credential is found, an AtlassianCredentialNotFoundException is thrown.
     *
     * @param request The request object containing the user's email, the current name of the credential,
     * and the new name to be assigned.
     * @return The updated AtlassianCredentialDto after the name change is successfully applied.
     * @throws AtlassianCredentialNotFoundException If no credential matches the provided email and current name.
     */
    @Tracked("Changing Atlassian credential name")
    fun changeCredentialName(authId: String, request: ChangeAtlassianCredentialNameRequest): AtlassianCredentialDto {
        val credential = credentialsRepository
            .findById(AtlassianCredentialId(authId, request.oldName))
            .orElseThrow {
                throw AtlassianCredentialNotFoundException(request.userEmail, request.oldName)
            }

        credential.id.name = request.newName
        credentialsRepository.save(credential)
        return credential.toDto()
    }

    /**
     * Updates the token of an Atlassian credential associated with the specified user email and token name.
     * If no matching credential is found, an AtlassianCredentialNotFoundException is thrown.
     *
     * @param request The request object containing the user's email, the name of the credential,
     * and the new token to be assigned.
     * @return The updated AtlassianCredentialDto after the token change is successfully applied.
     * @throws AtlassianCredentialNotFoundException If no credential matches the provided email and token name.
     */
    @Tracked("Changing Atlassian credential name")
    fun changeCredentialToken(authId: String, request: ChangeAtlassianCredentialTokenRequest): AtlassianCredentialDto {
        val credential = credentialsRepository
            .findById(AtlassianCredentialId(authId, request.tokenName))
            .orElseThrow {
                throw AtlassianCredentialNotFoundException(request.userEmail, request.tokenName)
            }

        credential.authToken = request.newToken
        credentialsRepository.save(credential)
        return credential.toDto()
    }
}
