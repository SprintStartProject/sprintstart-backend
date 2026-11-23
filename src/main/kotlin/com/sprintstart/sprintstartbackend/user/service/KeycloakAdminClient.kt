package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.security.KeycloakRoleMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID

interface KeycloakAdminClient {
    fun updateUserProfile(
        authId: String,
        email: String? = null,
        firstName: String? = null,
        lastName: String? = null,
        projectIds: Set<UUID>,
    )

    fun setUserEnabled(authId: String, enabled: Boolean)

    fun setPermissionGroup(authId: String, permissionGroup: Role)

    fun getPermissionGroups(authId: String): Set<Role>

    fun deleteUser(authId: String)
}

@Service
class HttpKeycloakAdminClient(
    private val tokenProvider: KeycloakAdminTokenProvider,
    private val roleClient: KeycloakRealmRoleClient,
    private val transport: KeycloakAdminTransport,
    private val uris: KeycloakAdminUris,
) : KeycloakAdminClient {
    private val objectMapper = jacksonObjectMapper()

    /**
     * Updates the user profile with the provided data.
     *
     * @param authId The unique identifier of the authenticated user.
     * @param email The new email address for the user. Can be null if no update is needed.
     * @param firstName The new first name for the user. Can be null if no update is needed.
     * @param lastName The new last name for the user. Can be null if no update is needed.
     * @param projectIds A set of project IDs associated with the user.
     */
    @Tracked("Updating user profile")
    override fun updateUserProfile(
        authId: String,
        email: String?,
        firstName: String?,
        lastName: String?,
        projectIds: Set<UUID>,
    ) {
        val payload = mutableMapOf<String, Any>()
        email?.let { payload["email"] = it }
        firstName?.let { payload["firstName"] = it }
        lastName?.let { payload["lastName"] = it }

        if (payload.isNotEmpty()) {
            putUser(authId, payload)
        }
    }

    /**
     * Updates the enabled status of a user within the system.
     *
     * @param authId The unique identifier of the user whose status is being updated.
     * @param enabled A boolean value indicating whether the user should be enabled (true) or disabled (false).
     */
    @Tracked("Updating user enabled status")
    override fun setUserEnabled(authId: String, enabled: Boolean) {
        putUser(authId, mapOf("enabled" to enabled))
    }

    /**
     * Assigns a specific permission group (role) to a user, replacing any currently managed roles.
     *
     * @param authId The unique identifier of the user for whom the permission group should be set.
     * @param permissionGroup The target role to be assigned to the user.
     */
    @Tracked("Setting permissions for user")
    override fun setPermissionGroup(authId: String, permissionGroup: Role) {
        val token = tokenProvider.accessToken()
        val currentRoles = roleClient.getRealmRoleMappings(authId, token)
        val managedCurrentRoles = currentRoles.filter {
            it["name"]?.stringValue() in KeycloakRoleMapper.managedRealmRoles()
        }

        if (managedCurrentRoles.isNotEmpty()) {
            transport.send(
                method = "DELETE",
                uri = uris.adminUri("/users/$authId/role-mappings/realm"),
                token = token,
                body = objectMapper.writeValueAsString(managedCurrentRoles),
            )
        }

        val targetRole = roleClient.getRealmRole(KeycloakRoleMapper.toRealmRole(permissionGroup), token)
        transport.send(
            method = "POST",
            uri = uris.adminUri("/users/$authId/role-mappings/realm"),
            token = token,
            body = objectMapper.writeValueAsString(listOf(targetRole)),
        )
    }

    /**
     * Retrieves the set of permission groups (roles) associated with the given authorization ID.
     *
     * @param authId The authorization ID for which the permission groups are to be retrieved.
     * @return A set of roles representing the permission groups associated with the provided authorization ID.
     * @throws ResponseStatusException If an error occurs during communication with the role client,
     *         except for a NOT_FOUND status, which is handled gracefully.
     */
    @Tracked("Retrieving permissions for user")
    override fun getPermissionGroups(authId: String): Set<Role> {
        val token = tokenProvider.accessToken()
        val roleMappings = try {
            roleClient.getCompositeRealmRoleMappings(authId, token)
        } catch (error: ResponseStatusException) {
            if (error.statusCode != HttpStatus.NOT_FOUND) {
                throw error
            }

            roleClient.getRealmRoleMappings(authId, token)
        }

        return KeycloakRoleMapper.mapRealmRoles(
            roleMappings.mapNotNull { it["name"]?.stringValue() },
        )
    }

    /**
     * Deletes a user identified by their authentication ID.
     *
     * @param authId The authentication ID of the user to be deleted.
     */
    @Tracked("Deleting user")
    override fun deleteUser(authId: String) {
        transport.send(
            method = "DELETE",
            uri = uris.adminUri("/users/$authId"),
            token = tokenProvider.accessToken(),
        )
    }

    /**
     * Updates or replaces a user's information in the system.
     *
     * @param authId The unique identifier of the user to be updated.
     * @param payload A map containing the key-value pairs of the user's data to be updated.
     */
    private fun putUser(authId: String, payload: Map<String, Any>) {
        transport.send(
            method = "PUT",
            uri = uris.adminUri("/users/$authId"),
            token = tokenProvider.accessToken(),
            body = objectMapper.writeValueAsString(payload),
        )
    }
}

@Component
class KeycloakRealmRoleClient(
    private val transport: KeycloakAdminTransport,
    private val uris: KeycloakAdminUris,
) {
    private val objectMapper = jacksonObjectMapper()

    /**
     * Retrieves a realm role from the server by its name.
     *
     * @param roleName The name of the role to retrieve.
     * @param token The authorization token for accessing the realm roles.
     * @return The realm role information as a JsonNode.
     */
    @Tracked("Retrieving realm role")
    fun getRealmRole(roleName: String, token: String): JsonNode {
        val body = transport.send(
            method = "GET",
            uri = uris.adminUri("/roles/${uris.encodePath(roleName)}"),
            token = token,
        )
        return objectMapper.readTree(body)
    }

    /**
     * Retrieves realm role mappings for a specific user.
     *
     * @param authId The ID of the user whose realm role mappings are being retrieved.
     * @param token The authentication token used for making the request.
     * @return A list of JsonNode objects representing the realm role mappings.
     */
    @Tracked("Retrieving realm role mappings")
    fun getRealmRoleMappings(authId: String, token: String): List<JsonNode> {
        val body = transport.send(
            method = "GET",
            uri = uris.adminUri("/users/$authId/role-mappings/realm"),
            token = token,
        )
        return objectMapper.readTree(body).toList()
    }

    /**
     * Retrieves the composite realm role mappings for a specific user.
     *
     * @param authId The identifier of the user whose role mappings are to be retrieved.
     * @param token The authentication token used for the API request.
     * @return A list of JsonNode objects representing the composite realm role mappings for the specified user.
     */
    @Tracked("Retrieving composite realm role mappings")
    fun getCompositeRealmRoleMappings(authId: String, token: String): List<JsonNode> {
        val body = transport.send(
            method = "GET",
            uri = uris.adminUri("/users/$authId/role-mappings/realm/composite"),
            token = token,
        )
        return objectMapper.readTree(body).toList()
    }
}
