package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.KeycloakAdminConfig
import com.sprintstart.sprintstartbackend.config.KeycloakRoleMapper
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
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
            it["name"]?.textValue() in KeycloakRoleMapper.managedRealmRoles()
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
            roleMappings.mapNotNull { it["name"]?.textValue() },
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

@Component
class KeycloakAdminTokenProvider(
    private val httpClient: HttpClient,
    private val applicationConfig: ApplicationConfig,
    private val uris: KeycloakAdminUris,
    @Value("\${KEYCLOAK_ADMIN:}")
    private val keycloakAdminUsername: String = "",
    @Value("\${KEYCLOAK_ADMIN_PASSWORD:}")
    private val keycloakAdminPassword: String = "",
) {
    private val objectMapper = jacksonObjectMapper()
    private val adminConfig get() = applicationConfig.keycloak.admin

    /**
     * Retrieves an access token from the authentication server.
     *
     * @return The access token as a string.
     * @throws ResponseStatusException if the request fails or the response does not contain the access token.
     */
    @Tracked("Retrieving access token")
    fun accessToken(): String {
        val form = tokenFormBody()
        val request = HttpRequest
            .newBuilder()
            .uri(uris.tokenRealmUri("/protocol/openid-connect/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Keycloak admin token request failed with status ${response.statusCode()}: " +
                    response.body().safeErrorBody(),
            )
        }

        return objectMapper.readTree(response.body())["access_token"]?.textValue()
            ?: throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Keycloak admin token response did not contain access_token",
            )
    }

    /**
     * Constructs a token form body string by encoding and joining key-value pairs.
     *
     * The method retrieves key-value pairs using `tokenFormPairs` and applies URL encoding
     * to both keys and values. The encoded pairs are joined with an "&" delimiter to form
     * a single string.
     *
     * @return A URL-encoded string representing the token form body.
     */
    private fun tokenFormBody(): String {
        val pairs = tokenFormPairs(adminConfig)
        return pairs.joinToString("&") { (key, value) -> "${uris.urlEncode(key)}=${uris.urlEncode(value)}" }
    }

    /**
     * Generates a list of key-value pairs representing token request parameters
     * based on the provided Keycloak admin configuration.
     *
     * @param config The KeycloakAdminConfig instance containing the necessary
     *               configuration details such as clientId, clientSecret,
     *               username, and password.
     * @return A list of pairs where each pair represents a token request parameter.
     *         If `clientSecret` is provided, the list will contain parameters for
     *         client credentials grant. Otherwise, if `username` and `password`
     *         are provided, the list will contain parameters for password credentials grant.
     * @throws ResponseStatusException Thrown with HttpStatus.BAD_GATEWAY if neither
     *                                 clientSecret nor username and password are configured properly.
     */
    private fun tokenFormPairs(config: KeycloakAdminConfig): List<Pair<String, String>> {
        val clientSecret = config.clientSecret
        val username = config.username.takeUnlessBlank() ?: keycloakAdminUsername.takeUnlessBlank()
        val password = config.password.takeUnlessBlank() ?: keycloakAdminPassword.takeUnlessBlank()

        return if (!clientSecret.isNullOrBlank()) {
            listOf(
                "grant_type" to "client_credentials",
                "client_id" to config.clientId,
                "client_secret" to clientSecret,
            )
        } else if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            listOf(
                "grant_type" to "password",
                "client_id" to config.clientId,
                "username" to username,
                "password" to password,
            )
        } else {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak admin credentials are not configured")
        }
    }
}

@Component
class KeycloakAdminTransport(
    private val httpClient: HttpClient,
) {
    /**
     * Sends an HTTP request to the specified URI using the given HTTP method and authorization token.
     *
     * @param method the HTTP method to be used (e.g., "GET", "POST", "PUT", etc.).
     * @param uri the target URI to which the request is sent.
     * @param token the authorization token to be included in the "Authorization" header.
     * @param body the optional request body to be sent. If null, the request is sent without a body.
     * @return the response body as a string.
     * @throws ResponseStatusException if the response status code is not within the successful range
     * (e.g., 2xx) or if the status code indicates an error such as "Not Found" or "Bad Gateway".
     */
    @Tracked("Sending HTTP request")
    fun send(method: String, uri: URI, token: String, body: String? = null): String {
        val request = HttpRequest
            .newBuilder()
            .uri(uri)
            .header("Authorization", "Bearer $token")
            .apply {
                if (body != null) {
                    header("Content-Type", "application/json")
                    this.method(method, HttpRequest.BodyPublishers.ofString(body))
                } else {
                    this.method(method, HttpRequest.BodyPublishers.noBody())
                }
            }.build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
            val status = if (response.statusCode() == HTTP_NOT_FOUND) HttpStatus.NOT_FOUND else HttpStatus.BAD_GATEWAY
            throw ResponseStatusException(
                status,
                "Keycloak admin request to $uri failed with status ${response.statusCode()}: " +
                    response.body().safeErrorBody(),
            )
        }

        return response.body()
    }
}

@Component
class KeycloakAdminUris(
    private val applicationConfig: ApplicationConfig,
    @Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private val jwtJwkSetUri: String = "",
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private val jwtIssuerUri: String = "",
) {
    private val adminConfig get() = applicationConfig.keycloak.admin

    fun tokenRealmUri(path: String): URI =
        URI.create("${keycloakBaseUrl()}/realms/${encodePath(adminConfig.tokenRealm)}$path")

    fun adminUri(path: String): URI =
        URI.create("${keycloakBaseUrl()}/admin/realms/${encodePath(adminConfig.realm)}$path")

    fun encodePath(value: String): String =
        urlEncode(value).replace("+", "%20")

    fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun keycloakBaseUrl(): String =
        adminConfig.baseUrl.takeUnlessBlank()?.trimEnd('/')
            ?: keycloakBaseUrlFromRealmUri(jwtJwkSetUri)
            ?: keycloakBaseUrlFromRealmUri(jwtIssuerUri)
            ?: throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Keycloak admin base URL could not be derived from JWT configuration",
            )

    private fun keycloakBaseUrlFromRealmUri(value: String): String? {
        val normalized = value.trim().trimEnd('/')
        val realmPathIndex = normalized.indexOf("/realms/")

        return if (normalized.isNotBlank() && realmPathIndex > 0) {
            normalized.substring(0, realmPathIndex)
        } else {
            null
        }
    }
}

private fun String.safeErrorBody(): String =
    take(MAX_ERROR_BODY_LENGTH).ifBlank { "empty response body" }

private fun String?.takeUnlessBlank(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }

private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_SUCCESS_MAX = 299
private const val HTTP_NOT_FOUND = 404
private const val MAX_ERROR_BODY_LENGTH = 500
