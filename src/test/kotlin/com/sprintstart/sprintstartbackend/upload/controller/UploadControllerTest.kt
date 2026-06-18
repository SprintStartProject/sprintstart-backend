package com.sprintstart.sprintstartbackend.upload.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.upload.model.dto.UploadArtifactResponse
import com.sprintstart.sprintstartbackend.upload.model.dto.UploadListItemResponse
import com.sprintstart.sprintstartbackend.upload.service.UploadService
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
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

    private val uploaderId = UUID.randomUUID()
    private val artifactId = UUID.randomUUID()

    private val userJwt = jwt()
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    private val noUserRoleJwt = jwt()
        .authorities(SimpleGrantedAuthority("ROLE_NONE"))

    private val sampleFile = MockMultipartFile(
        "files",
        "test.md",
        "text/markdown",
        "# hello".toByteArray(),
    )

    // ========================== upload ==========================

    @Test
    fun `upload returns 200 with uploaded artifacts`() {
        val response = listOf(UploadArtifactResponse(id = UUID.randomUUID(), filename = "test.md", status = "ok"))
        every { uploadService.upload(any(), uploaderId) } returns response

        mockMvc
            .perform(
                multipart("/api/v1/uploads")
                    .file(sampleFile)
                    .param("uploaderId", uploaderId.toString())
                    .with(userJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].filename").value("test.md"))
            .andExpect(jsonPath("$[0].status").value("ok"))

        verify { uploadService.upload(any(), uploaderId) }
    }

    @Test
    fun `upload returns 401 when not authenticated`() {
        mockMvc
            .perform(
                multipart("/api/v1/uploads")
                    .file(sampleFile)
                    .param("uploaderId", uploaderId.toString()),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `upload returns 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                multipart("/api/v1/uploads")
                    .file(sampleFile)
                    .param("uploaderId", uploaderId.toString())
                    .with(noUserRoleJwt),
            ).andExpect(status().isForbidden)
    }

    // ========================== listUploads ==========================

    @Test
    fun `listUploads returns 200 with uploader artifacts`() {
        val uploads = listOf(
            UploadListItemResponse(
                id = artifactId,
                filename = "doc.md",
                mime = "text/markdown",
                uploadedAt = Instant.now(),
            ),
        )
        every { uploadService.listUploads(uploaderId) } returns uploads

        mockMvc
            .perform(
                get("/api/v1/uploads")
                    .param("uploaderId", uploaderId.toString())
                    .with(userJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].filename").value("doc.md"))
            .andExpect(jsonPath("$[0].mime").value("text/markdown"))

        verify { uploadService.listUploads(uploaderId) }
    }

    @Test
    fun `listUploads returns 401 when not authenticated`() {
        mockMvc
            .perform(
                get("/api/v1/uploads")
                    .param("uploaderId", uploaderId.toString()),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `listUploads returns 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                get("/api/v1/uploads")
                    .param("uploaderId", uploaderId.toString())
                    .with(noUserRoleJwt),
            ).andExpect(status().isForbidden)
    }

    // ========================== deleteUpload ==========================

    @Test
    fun `deleteUpload returns 204`() {
        every { uploadService.deleteUpload(artifactId) } returns Unit

        mockMvc
            .perform(
                delete("/api/v1/uploads/$artifactId")
                    .with(userJwt),
            ).andExpect(status().isNoContent)

        verify { uploadService.deleteUpload(artifactId) }
    }

    @Test
    fun `deleteUpload returns 401 when not authenticated`() {
        mockMvc
            .perform(delete("/api/v1/uploads/$artifactId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `deleteUpload returns 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                delete("/api/v1/uploads/$artifactId")
                    .with(noUserRoleJwt),
            ).andExpect(status().isForbidden)
    }
}
