package com.sprintstart.sprintstartbackend.github.service

import com.sprintstart.sprintstartbackend.github.models.GithubUser
import com.sprintstart.sprintstartbackend.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.github.models.api.requests.AddPatRequest
import com.sprintstart.sprintstartbackend.github.models.api.requests.RemovePatRequest
import com.sprintstart.sprintstartbackend.github.models.api.requests.UpdatePatNameRequest
import com.sprintstart.sprintstartbackend.github.models.api.requests.UpdatePatRequest
import com.sprintstart.sprintstartbackend.github.models.exceptions.GithubUserPatNameAlreadyExistsException
import com.sprintstart.sprintstartbackend.github.models.exceptions.GithubUserPatNotFoundException
import com.sprintstart.sprintstartbackend.github.models.exceptions.GithubUserPatStillInUseException
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.github.repository.GithubUserRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service responsible for managing GitHub user data.
 * Handles operations such as retrieving, adding, updating,
 * and removing personal access tokens (PATs) for GitHub users.
 *
 * @constructor Creates an instance of GithubUserService with the specified repositories.
 * @param githubUserRepository Repository for interacting with GitHub user data.
 * @param githubRepositoryConnectionRepository Repository for handling connections to GitHub repositories.
 */
@Service
class GithubUserService(
    private val githubUserRepository: GithubUserRepository,
    private val githubRepositoryConnectionRepository: GithubRepositoryConnectionRepository,
) {
    /**
     * Retrieves the names of all personal access tokens (PATs) associated with the given authentication ID.
     *
     * @param authId The authentication ID of the user whose PATs are to be retrieved.
     * @return A list of personal access tokens (PATs) associated with the specified authentication ID.
     */
    @Tracked("Retrieving all GitHub PATs")
    @Transactional(readOnly = true)
    fun getAllPATNames(authId: String): List<String> =
        githubUserRepository.findAllByAuthId(authId)

    /**
     * Adds a new personal access token (PAT) for a GitHub user.
     *
     * @param authId The identifier of the authenticated user.
     * @param request The request object containing the details of the personal access token to be added.
     */
    @Tracked("Adding new GitHub PAT")
    fun addPAT(authId: String, request: AddPatRequest) {
        if (githubUserRepository.findById(GithubUserPat(authId = authId, name = request.name)).isPresent) {
            throw GithubUserPatNameAlreadyExistsException(request.name)
        }

        val userPat = GithubUserPat(authId = authId, name = request.name)
        val entity = GithubUser(userPat, request.token)
        githubUserRepository.save(entity)
    }

    /**
     * Updates the PAT (Personal Access Token) of a GitHub user identified by the provided name
     * within the repository. If the user is not found, an exception is thrown.
     *
     * @param authId The authentication ID of the requesting user.
     * @param request The request containing the name of the GitHub user and the new PAT to be updated.
     */
    @Tracked("Updating GitHub PAT")
    fun updatePAT(authId: String, request: UpdatePatRequest) {
        val userPatEntity =
            githubUserRepository.findById(GithubUserPat(authId = authId, name = request.name)).orElseThrow {
                GithubUserPatNotFoundException(
                    request.name,
                    authId,
                )
            }
        userPatEntity.token = request.newToken
        githubUserRepository.save(userPatEntity)
    }

    /**
     * Updates the PAT (Personal Access Token) name for a user identified by the authentication ID.
     *
     * @param authId The authentication ID of the user whose PAT name is to be updated.
     * @param request The request object containing the old name and the new name for the PAT.
     * @throws GithubUserPatNotFoundException If no PAT is found with the specified name for the given user.
     */
    @Transactional
    @Tracked("Updating GitHub PAT name")
    fun updatePATName(authId: String, request: UpdatePatNameRequest) {
        githubUserRepository.updatePatName(authId, request.oldName, request.newName).takeIf { it == 1 }
            ?: throw GithubUserPatNotFoundException(
                request.oldName,
                authId,
            )
    }

    /**
     * Removes a personal access token (PAT) associated with a GitHub user.
     *
     * @param authId The authorization ID of the user performing the operation.
     * @param request The request object containing the details of the PAT to be removed, including the name of the PAT.
     * @throws GithubUserPatNotFoundException If no PAT is found with the specified name for the given user.
     */
    @Tracked("Deleting GitHub PAT")
    fun removePAT(authId: String, request: RemovePatRequest) {
        val userPat = githubUserRepository.findById(GithubUserPat(authId = authId, name = request.name)).orElseThrow {
            GithubUserPatNotFoundException(
                request.name,
                authId,
            )
        }

        // Delete only if not still in use
        if (githubRepositoryConnectionRepository.findByUser(userPat).isEmpty()) {
            githubUserRepository.delete(userPat)
        } else {
            throw GithubUserPatStillInUseException(userPat.id.name)
        }
    }
}
