package com.sprintstart.sprintstartbackend.user

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.service.HttpKeycloakAdminClient
import com.sprintstart.sprintstartbackend.user.service.KeycloakAdminTokenProvider
import com.sprintstart.sprintstartbackend.user.service.KeycloakAdminTransport
import com.sprintstart.sprintstartbackend.user.service.KeycloakAdminUris
import com.sprintstart.sprintstartbackend.user.service.KeycloakRealmRoleClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI

class KeycloakAdminClientTest {
    private val tokenProvider: KeycloakAdminTokenProvider = mockk()
    private val roleClient: KeycloakRealmRoleClient = mockk()
    private val transport: KeycloakAdminTransport = mockk()
    private val uris: KeycloakAdminUris = mockk()
    private val client = HttpKeycloakAdminClient(tokenProvider, roleClient, transport, uris)
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `updateUserProfile sends only provided profile fields to Keycloak`() {
        val userUri = URI.create("https://keycloak.local/admin/realms/sprintstart/users/auth-1")
        val body = slot<String>()
        every { tokenProvider.accessToken() } returns "token"
        every { uris.adminUri("/users/auth-1") } returns userUri
        every { transport.send("PUT", userUri, "token", capture(body)) } returns ""

        client.updateUserProfile(
            authId = "auth-1",
            email = "new@mail.de",
            firstName = "Alicia",
            lastName = null,
        )

        val payload = objectMapper.readTree(body.captured)
        assertThat(payload["email"].stringValue()).isEqualTo("new@mail.de")
        assertThat(payload["firstName"].stringValue()).isEqualTo("Alicia")
        assertThat(payload.has("lastName")).isFalse()
        verify(exactly = 1) { transport.send("PUT", userUri, "token", body.captured) }
    }

    @Test
    fun `updateUserProfile skips Keycloak request when no profile fields are provided`() {
        client.updateUserProfile(authId = "auth-1")

        verify(exactly = 0) { tokenProvider.accessToken() }
        verify(exactly = 0) { transport.send(any(), any(), any(), any()) }
    }

    @Test
    fun `setUserEnabled sends enabled state to Keycloak user endpoint`() {
        val userUri = URI.create("https://keycloak.local/admin/realms/sprintstart/users/auth-1")
        val body = slot<String>()
        every { tokenProvider.accessToken() } returns "token"
        every { uris.adminUri("/users/auth-1") } returns userUri
        every { transport.send("PUT", userUri, "token", capture(body)) } returns ""

        client.setUserEnabled("auth-1", false)

        val payload = objectMapper.readTree(body.captured)
        assertThat(payload["enabled"].asBoolean()).isFalse()
        verify(exactly = 1) { transport.send("PUT", userUri, "token", body.captured) }
    }

    @Test
    fun `setPermissionGroup replaces managed realm roles with target role`() {
        val roleMappingsUri = URI
            .create("https://keycloak.local/admin/realms/sprintstart/users/auth-1/role-mappings/realm")
        val deleteBody = slot<String>()
        val postBody = slot<String>()
        every { tokenProvider.accessToken() } returns "token"
        every { roleClient.getRealmRoleMappings("auth-1", "token") } returns listOf(
            jsonNode("""{"id":"role-user","name":"user"}"""),
            jsonNode("""{"id":"outside-role","name":"unmanaged"}"""),
        )
        every { uris.adminUri("/users/auth-1/role-mappings/realm") } returns roleMappingsUri
        every { transport.send("DELETE", roleMappingsUri, "token", capture(deleteBody)) } returns ""
        every { roleClient.getRealmRole("admin", "token") } returns jsonNode("""{"id":"role-admin","name":"admin"}""")
        every { transport.send("POST", roleMappingsUri, "token", capture(postBody)) } returns ""

        client.setPermissionGroup("auth-1", Role.ADMIN)

        val removedRoles = objectMapper.readTree(deleteBody.captured)
        assertThat(removedRoles).hasSize(1)
        assertThat(removedRoles[0]["name"].stringValue()).isEqualTo("user")

        val addedRoles = objectMapper.readTree(postBody.captured)
        assertThat(addedRoles).hasSize(1)
        assertThat(addedRoles[0]["name"].stringValue()).isEqualTo("admin")
    }

    @Test
    fun `deleteUser sends delete request to Keycloak user endpoint`() {
        val userUri = URI.create("https://keycloak.local/admin/realms/sprintstart/users/auth-1")
        every { tokenProvider.accessToken() } returns "token"
        every { uris.adminUri("/users/auth-1") } returns userUri
        every { transport.send("DELETE", userUri, "token", null) } returns ""

        client.deleteUser("auth-1")

        verify(exactly = 1) { transport.send("DELETE", userUri, "token", null) }
    }

    private fun jsonNode(value: String): JsonNode =
        objectMapper.readTree(value)
}
