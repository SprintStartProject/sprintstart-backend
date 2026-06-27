package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.KeycloakAdminConfig
import com.sprintstart.sprintstartbackend.config.KeycloakRoleMapper
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

interface KeycloakAdminClient {
    fun updateUserProfile(
        authId: String,
        email: String? = null,
        firstName: String? = null,
        lastName: String? = null,
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

    override fun updateUserProfile(authId: String, email: String?, firstName: String?, lastName: String?) {
        val payload = mutableMapOf<String, Any>()
        email?.let { payload["email"] = it }
        firstName?.let { payload["firstName"] = it }
        lastName?.let { payload["lastName"] = it }

        if (payload.isNotEmpty()) {
            putUser(authId, payload)
        }
    }

    override fun setUserEnabled(authId: String, enabled: Boolean) {
        putUser(authId, mapOf("enabled" to enabled))
    }

    override fun setPermissionGroup(authId: String, permissionGroup: Role) {
        val token = tokenProvider.accessToken()
        val currentRoles = roleClient.getRealmRoleMappings(authId, token)
        val managedCurrentRoles = currentRoles.filter { it["name"]?.asText() in KeycloakRoleMapper.managedRealmRoles() }

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
            roleMappings.mapNotNull { it["name"]?.asText() },
        )
    }

    override fun deleteUser(authId: String) {
        transport.send(
            method = "DELETE",
            uri = uris.adminUri("/users/$authId"),
            token = tokenProvider.accessToken(),
        )
    }

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

    fun getRealmRole(roleName: String, token: String): JsonNode {
        val body = transport.send(
            method = "GET",
            uri = uris.adminUri("/roles/${uris.encodePath(roleName)}"),
            token = token,
        )
        return objectMapper.readTree(body)
    }

    fun getRealmRoleMappings(authId: String, token: String): List<JsonNode> {
        val body = transport.send(
            method = "GET",
            uri = uris.adminUri("/users/$authId/role-mappings/realm"),
            token = token,
        )
        return objectMapper.readTree(body).toList()
    }

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

        return objectMapper.readTree(response.body())["access_token"]?.asText()
            ?: throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Keycloak admin token response did not contain access_token",
            )
    }

    private fun tokenFormBody(): String {
        val pairs = tokenFormPairs(adminConfig)
        return pairs.joinToString("&") { (key, value) -> "${uris.urlEncode(key)}=${uris.urlEncode(value)}" }
    }

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
