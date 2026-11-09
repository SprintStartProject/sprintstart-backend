package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.shared.config.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.config.KeycloakAdminConfig
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.util.concurrent.Flow

class KeycloakAdminHttpTest {
    private val httpClient = mockk<HttpClient>()
    private val transport = KeycloakAdminTransport(httpClient)
    private val uri = URI.create("http://keycloak/admin/realms/sprintstart/users/1")

    private fun respond(status: Int, body: String) {
        val response = mockk<HttpResponse<String>> {
            every { statusCode() } returns status
            every { body() } returns body
        }
        every { httpClient.send(any(), any<HttpResponse.BodyHandler<String>>()) } returns response
    }

    private fun header(request: HttpRequest, name: String): String? = request.headers().firstValue(name).orElse(null)

    @Test
    fun `a successful request returns the body and carries the bearer token`() {
        val sent = slot<HttpRequest>()
        val response = mockk<HttpResponse<String>> {
            every { statusCode() } returns 204
            every { body() } returns "done"
        }
        every { httpClient.send(capture(sent), any<HttpResponse.BodyHandler<String>>()) } returns response

        val body = transport.send("PUT", uri, "tok", """{"a":1}""")

        assertThat(body).isEqualTo("done")
        assertThat(sent.captured.method()).isEqualTo("PUT")
        assertThat(header(sent.captured, "Authorization")).isEqualTo("Bearer tok")
        assertThat(header(sent.captured, "Content-Type")).isEqualTo("application/json")
    }

    @Test
    fun `a request without a body sends no content type`() {
        val sent = slot<HttpRequest>()
        val response = mockk<HttpResponse<String>> {
            every { statusCode() } returns 200
            every { body() } returns "[]"
        }
        every { httpClient.send(capture(sent), any<HttpResponse.BodyHandler<String>>()) } returns response

        transport.send("GET", uri, "tok")

        assertThat(header(sent.captured, "Content-Type")).isNull()
    }

    @Test
    fun `a missing resource is a 404 and carries what Keycloak said`() {
        respond(404, "no such user")

        val error = assertThrows<ResponseStatusException> { transport.send("GET", uri, "tok") }

        assertThat(error.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(error.reason).contains("404").contains("no such user").contains(uri.toString())
    }

    @Test
    fun `any other failure is a 502, with a placeholder for an empty body`() {
        respond(500, "  ")

        val error = assertThrows<ResponseStatusException> { transport.send("GET", uri, "tok") }

        assertThat(error.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
        assertThat(error.reason).contains("500").contains("empty response body")
    }

    @Test
    fun `a long error body is cut off`() {
        respond(502, "x".repeat(2_000))

        val error = assertThrows<ResponseStatusException> { transport.send("GET", uri, "tok") }

        assertThat(error.reason!!.substringAfter(": ")).hasSize(500)
    }

    @Test
    fun `the redirect boundary statuses are failures and the success boundaries are not`() {
        respond(200, "ok")
        assertThat(transport.send("GET", uri, "t")).isEqualTo("ok")
        respond(299, "ok")
        assertThat(transport.send("GET", uri, "t")).isEqualTo("ok")
        respond(300, "x")
        assertThrows<ResponseStatusException> { transport.send("GET", uri, "t") }
        respond(199, "x")
        assertThrows<ResponseStatusException> { transport.send("GET", uri, "t") }
    }

    private fun uris(baseUrl: String?, jwkSetUri: String = "", issuerUri: String = ""): KeycloakAdminUris {
        val config = mockk<ApplicationConfig>()
        every { config.keycloak.admin } returns KeycloakAdminConfig(baseUrl = baseUrl)
        return KeycloakAdminUris(config, jwkSetUri, issuerUri)
    }

    @Test
    fun `a configured base url builds the admin and token urls, without a trailing slash`() {
        val uris = uris("http://kc:8080/")

        assertThat(uris.adminUri("/users")).isEqualTo(URI.create("http://kc:8080/admin/realms/sprintstart/users"))
        assertThat(uris.tokenRealmUri("/x")).isEqualTo(URI.create("http://kc:8080/realms/master/x"))
    }

    @Test
    fun `without a base url it is derived from the jwk set uri, then the issuer uri`() {
        assertThat(uris(" ", jwkSetUri = "http://a/realms/r/protocol/certs").adminUri("/u").toString())
            .startsWith("http://a/admin/realms/")
        assertThat(uris(null, issuerUri = "http://b/realms/r/").adminUri("/u").toString())
            .startsWith("http://b/admin/realms/")
    }

    @Test
    fun `with nothing to derive it from the base url is a 502`() {
        val error = assertThrows<ResponseStatusException> { uris(null, "http://no-realm-here", "").adminUri("/u") }

        assertThat(error.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
    }

    @Test
    fun `path segments are percent encoded with spaces as percent 20`() {
        val uris = uris("http://kc")

        assertThat(uris.encodePath("a b/c")).isEqualTo("a%20b%2Fc")
        assertThat(uris.urlEncode("a b")).isEqualTo("a+b")
    }

    private fun tokenProvider(
        admin: KeycloakAdminConfig,
        username: String = "",
        password: String = "",
    ): KeycloakAdminTokenProvider {
        val config = mockk<ApplicationConfig>()
        every { config.keycloak.admin } returns admin
        return KeycloakAdminTokenProvider(httpClient, config, KeycloakAdminUris(config), username, password)
    }

    private fun sentTokenRequest(body: String, status: Int = 200): SentRequest {
        val sent = slot<HttpRequest>()
        val response = mockk<HttpResponse<String>> {
            every { statusCode() } returns status
            every { body() } returns body
        }
        every { httpClient.send(capture(sent), any<HttpResponse.BodyHandler<String>>()) } returns response
        return SentRequest(sent)
    }

    private class SentRequest(
        val sent: CapturingSlot<HttpRequest>,
    )

    private fun formOf(request: HttpRequest): String {
        val out = ByteArrayOutputStream()
        request.bodyPublisher().get().subscribe(
            object : Flow.Subscriber<ByteBuffer> {
                override fun onSubscribe(s: Flow.Subscription) = s.request(Long.MAX_VALUE)

                override fun onNext(item: ByteBuffer) {
                    val bytes = ByteArray(item.remaining())
                    item.get(bytes)
                    out.write(bytes)
                }

                override fun onError(t: Throwable) = throw t

                override fun onComplete() = Unit
            },
        )
        return out.toString(Charsets.UTF_8)
    }

    @Test
    fun `a client secret asks for a client credentials token`() {
        val holder = sentTokenRequest("""{"access_token":"abc"}""")
        val provider = tokenProvider(KeycloakAdminConfig(baseUrl = "http://kc", clientSecret = "s3 cret"))

        assertThat(provider.accessToken()).isEqualTo("abc")

        val request = holder.sent.captured
        assertThat(request.uri()).isEqualTo(URI.create("http://kc/realms/master/protocol/openid-connect/token"))
        assertThat(header(request, "Content-Type")).isEqualTo("application/x-www-form-urlencoded")
        assertThat(formOf(request))
            .isEqualTo("grant_type=client_credentials&client_id=admin-cli&client_secret=s3+cret")
    }

    @Test
    fun `without a secret a username and password ask for a password token, the config before the environment`() {
        val holder = sentTokenRequest("""{"access_token":"abc"}""")
        val provider = tokenProvider(
            KeycloakAdminConfig(baseUrl = "http://kc", username = " admin ", password = null),
            username = "env-user",
            password = "env-pass",
        )

        provider.accessToken()

        assertThat(formOf(holder.sent.captured))
            .isEqualTo("grant_type=password&client_id=admin-cli&username=admin&password=env-pass")
    }

    @Test
    fun `no credentials at all is a 502`() {
        val provider = tokenProvider(KeycloakAdminConfig(baseUrl = "http://kc", username = "only-a-user"))

        val error = assertThrows<ResponseStatusException> { provider.accessToken() }

        assertThat(error.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
        assertThat(error.reason).contains("credentials are not configured")
    }

    @Test
    fun `a failed token request is a 502 carrying what Keycloak said`() {
        sentTokenRequest("denied", status = 401)
        val provider = tokenProvider(KeycloakAdminConfig(baseUrl = "http://kc", clientSecret = "s"))

        val error = assertThrows<ResponseStatusException> { provider.accessToken() }

        assertThat(error.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
        assertThat(error.reason).contains("401").contains("denied")
    }

    @Test
    fun `a token response without an access token is a 502`() {
        sentTokenRequest("""{"other":"x"}""")
        val provider = tokenProvider(KeycloakAdminConfig(baseUrl = "http://kc", clientSecret = "s"))

        val error = assertThrows<ResponseStatusException> { provider.accessToken() }

        assertThat(error.reason).contains("did not contain access_token")
    }

    @Test
    fun `the uris and the token provider can be built from the configuration alone`() {
        val config = mockk<ApplicationConfig>()
        every { config.keycloak.admin } returns KeycloakAdminConfig(baseUrl = "http://kc")

        val uris = KeycloakAdminUris(config)

        assertThat(uris.adminUri("/u")).isEqualTo(URI.create("http://kc/admin/realms/sprintstart/u"))
        assertThrows<ResponseStatusException> { KeycloakAdminTokenProvider(httpClient, config, uris).accessToken() }
    }
}
