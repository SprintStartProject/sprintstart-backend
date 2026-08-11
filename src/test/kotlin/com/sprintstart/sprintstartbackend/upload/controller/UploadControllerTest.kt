package com.sprintstart.sprintstartbackend.upload.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.upload.model.dto.response.UploadArtifactResponse
import com.sprintstart.sprintstartbackend.upload.model.dto.response.UploadListItemResponse
import com.sprintstart.sprintstartbackend.upload.service.UploadService
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(UploadController::class)
@Import(
    SecurityConfig::class,
)
@AutoConfigureMockMvc
class UploadControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var uploadService: UploadService

    @MockkBean
    lateinit var jwtDecoder: JwtDecoder

    private val projectId = UUID.randomUUID()
    private val authId = "auth-user"
    private val artifactId = UUID.randomUUID()

    private val userJwt = jwt()
        .jwt { it.subject(authId) }
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    private val pmJwt = jwt()
        .jwt { it.subject(authId) }
        .authorities(SimpleGrantedAuthority("ROLE_PM"))

    private val adminJwt = jwt()
        .jwt { it.subject(authId) }
        .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    private val noUserRoleJwt = jwt()
        .authorities(SimpleGrantedAuthority("ROLE_NONE"))

    private val sampleFile = MockMultipartFile(
        "files",
        "test.md",
        "text/markdown",
        "# hello".toByteArray(),
    )

    private val uploadRequest = MockMultipartFile(
        "request",
        "request.json",
        "application/json",
        """{"projectId":"$projectId"}""".toByteArray(),
    )

    private val deleteRequest = MockMultipartFile(
        "request",
        "request.json",
        "application/json",
        """{"artifactIds":["$artifactId"],"projectId":"$projectId"}""".toByteArray(),
    )

    // ========================== upload ==========================

    @Test
    fun `upload returns 200 for PM with uploaded artifacts`() {
        val response = listOf(UploadArtifactResponse(id = UUID.randomUUID(), filename = "test.md", status = "ok"))
        every { uploadService.upload(authId, any(), projectId) } returns response

        mockMvc
            .perform(
                multipart("/api/v1/uploads")
                    .file(sampleFile)
                    .file(uploadRequest)
                    .with(pmJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].filename").value("test.md"))
            .andExpect(jsonPath("$[0].status").value("ok"))

        verify { uploadService.upload(authId, any(), projectId) }
    }

    @Test
    fun `upload returns 200 for admin with uploaded artifacts`() {
        val response = listOf(UploadArtifactResponse(id = UUID.randomUUID(), filename = "test.md", status = "ok"))
        every { uploadService.upload(authId, any(), projectId) } returns response

        mockMvc
            .perform(
                multipart("/api/v1/uploads")
                    .file(sampleFile)
                    .file(uploadRequest)
                    .with(adminJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].filename").value("test.md"))

        verify { uploadService.upload(authId, any(), projectId) }
    }

    @Test
    fun `upload returns 401 when not authenticated`() {
        mockMvc
            .perform(
                multipart("/api/v1/uploads")
                    .file(sampleFile)
                    .file(uploadRequest),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `upload returns 403 for normal user`() {
        mockMvc
            .perform(
                multipart("/api/v1/uploads")
                    .file(sampleFile)
                    .file(uploadRequest)
                    .with(userJwt),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `upload returns 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                multipart("/api/v1/uploads")
                    .file(sampleFile)
                    .file(uploadRequest)
                    .with(noUserRoleJwt),
            ).andExpect(status().isForbidden)
    }

    // ========================== listUploads ==========================

    @Test
    fun `listUploads returns 200 for PM with project artifacts`() {
        val uploads = listOf(
            UploadListItemResponse(
                id = artifactId,
                filename = "doc.md",
                mime = "text/markdown",
                uploadedAt = Instant.now(),
            ),
        )
        every { uploadService.listUploads(authId, projectId) } returns uploads

        mockMvc
            .perform(
                get("/api/v1/uploads")
                    .param("projectId", projectId.toString())
                    .with(pmJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].filename").value("doc.md"))
            .andExpect(jsonPath("$[0].mime").value("text/markdown"))

        verify { uploadService.listUploads(authId, projectId) }
    }

    @Test
    fun `listUploads returns 200 for admin with project artifacts`() {
        val uploads = listOf(
            UploadListItemResponse(
                id = artifactId,
                filename = "doc.md",
                mime = "text/markdown",
                uploadedAt = Instant.now(),
            ),
        )
        every { uploadService.listUploads(authId, projectId) } returns uploads

        mockMvc
            .perform(
                get("/api/v1/uploads")
                    .param("projectId", projectId.toString())
                    .with(adminJwt),
            ).andExpect(status().isOk)

        verify { uploadService.listUploads(authId, projectId) }
    }

    @Test
    fun `listUploads returns 401 when not authenticated`() {
        mockMvc
            .perform(
                get("/api/v1/uploads")
                    .param("projectId", projectId.toString()),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `listUploads returns 403 for normal user`() {
        mockMvc
            .perform(
                get("/api/v1/uploads")
                    .param("projectId", projectId.toString())
                    .with(userJwt),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `listUploads returns 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                get("/api/v1/uploads")
                    .param("projectId", projectId.toString())
                    .with(noUserRoleJwt),
            ).andExpect(status().isForbidden)
    }

    // ========================== deleteUpload ==========================

    @Test
    fun `deleteUpload returns 204 for PM`() {
        every {
            uploadService.deleteUpload(authId, setOf(artifactId), projectId)
        } returns Unit

        mockMvc
            .perform(
                multipart(HttpMethod.DELETE, "/api/v1/uploads")
                    .file(deleteRequest)
                    .with(pmJwt),
            ).andExpect(status().isNoContent)

        verify { uploadService.deleteUpload(authId, setOf(artifactId), projectId) }
    }

    @Test
    fun `deleteUpload returns 204 for admin`() {
        every {
            uploadService.deleteUpload(authId, setOf(artifactId), projectId)
        } returns Unit

        mockMvc
            .perform(
                multipart(HttpMethod.DELETE, "/api/v1/uploads")
                    .file(deleteRequest)
                    .with(adminJwt),
            ).andExpect(status().isNoContent)

        verify { uploadService.deleteUpload(authId, setOf(artifactId), projectId) }
    }

    @Test
    fun `deleteUpload returns 401 when not authenticated`() {
        mockMvc
            .perform(
                multipart(HttpMethod.DELETE, "/api/v1/uploads")
                    .file(deleteRequest),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `deleteUpload returns 403 for normal user`() {
        mockMvc
            .perform(
                multipart(HttpMethod.DELETE, "/api/v1/uploads")
                    .file(deleteRequest)
                    .with(userJwt),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `deleteUpload returns 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                multipart(HttpMethod.DELETE, "/api/v1/uploads")
                    .file(deleteRequest)
                    .with(noUserRoleJwt),
            ).andExpect(status().isForbidden)
    }
}
