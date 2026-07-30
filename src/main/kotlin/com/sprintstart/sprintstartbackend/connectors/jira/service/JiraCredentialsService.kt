package com.sprintstart.sprintstartbackend.connectors.jira.service

import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.AddCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.ChangeJiraCredentialNameRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.ChangeJiraCredentialTokenRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.DeleteJiraCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.credentials.JiraCredentialsDto
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.credentials.toDto
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraCredential
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraCredentialsId
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraCredentialAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraCredentialsRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
internal class JiraCredentialsService(
    private val credentialsRepository: JiraCredentialsRepository,
) {
    /**
     * Adds a new Jira credential to the repository if it does not already exist.
     *
     * @param request The request object containing the details of the credential to add, including
     * the user's email, the name of the token, and the authentication token.
     * @throws JiraCredentialAlreadyExistsException If a credential with the specified user email
     * and token name already exists.
     */
    @Tracked("Storing a new Jira credential")
    fun addCredentials(request: AddCredentialRequest) {
        if (credentialsRepository.existsById(JiraCredentialsId(request.userEmail, request.tokenName))) {
            throw JiraCredentialAlreadyExistsException(request.userEmail, request.tokenName)
        }

        val credentials = JiraCredential(JiraCredentialsId(request.userEmail, request.tokenName), request.authToken)
        credentialsRepository.save(credentials)
    }

    /**
     * Retrieves all Jira credentials associated with a specific user based on their email.
     *
     * @param userEmail The email address of the user whose Jira credentials are to be retrieved.
     * @return A list of JiraCredentialsDto containing the user's Jira credential details.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving Jira credentials of a user")
    fun getCredentialsOfUser(userEmail: String): List<JiraCredentialsDto> =
        credentialsRepository.findAllByUserEmail(userEmail).map { it.toDto() }

    /**
     * Removes a Jira credential associated with the specified user email and token name.
     * If no matching credential is found, a JiraCredentialNotFoundException is thrown.
     *
     * @param request The request object containing the details of the credential to be removed,
     * including the user's email and the token name.
     * @throws JiraCredentialNotFoundException If no credential matches the provided email and token name.
     */
    @Tracked("Removing Jira credentials")
    fun removeCredential(request: DeleteJiraCredentialRequest) {
        if (!credentialsRepository.existsById(JiraCredentialsId(request.userEmail, request.tokenName))) {
            throw JiraCredentialNotFoundException(request.userEmail, request.tokenName)
        }

        credentialsRepository.deleteById(JiraCredentialsId(request.userEmail, request.tokenName))
    }

    /**
     * Updates the name of a Jira credential associated with the specified user email and current name.
     * If no matching credential is found, a JiraCredentialNotFoundException is thrown.
     *
     * @param request The request object containing the user's email, the current name of the credential,
     * and the new name to be assigned.
     * @return The updated JiraCredentialsDto after the name change is successfully applied.
     * @throws JiraCredentialNotFoundException If no credential matches the provided email and current name.
     */
    @Tracked("Changing Jira credential name")
    fun changeCredentialName(request: ChangeJiraCredentialNameRequest): JiraCredentialsDto {
        val credential = credentialsRepository
            .findById(JiraCredentialsId(request.userEmail, request.oldName))
            .orElseThrow {
                throw JiraCredentialNotFoundException(request.userEmail, request.oldName)
            }

        credential.id.name = request.newName
        credentialsRepository.save(credential)
        return credential.toDto()
    }

    /**
     * Updates the token of a Jira credential associated with the specified user email and token name.
     * If no matching credential is found, a JiraCredentialNotFoundException is thrown.
     *
     * @param request The request object containing the user's email, the name of the credential,
     * and the new token to be assigned.
     * @return The updated JiraCredentialsDto after the token change is successfully applied.
     * @throws JiraCredentialNotFoundException If no credential matches the provided email and token name.
     */
    @Tracked("Changing Jira credential name")
    fun changeCredentialToken(request: ChangeJiraCredentialTokenRequest): JiraCredentialsDto {
        val credential = credentialsRepository
            .findById(JiraCredentialsId(request.userEmail, request.tokenName))
            .orElseThrow {
                throw JiraCredentialNotFoundException(request.userEmail, request.tokenName)
            }

        credential.authToken = request.newToken
        credentialsRepository.save(credential)
        return credential.toDto()
    }
}
