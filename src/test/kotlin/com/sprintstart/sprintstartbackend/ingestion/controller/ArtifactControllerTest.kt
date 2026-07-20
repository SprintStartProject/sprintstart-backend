package com.sprintstart.sprintstartbackend.ingestion.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactContentRedirectResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactContentResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactPageResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.PageMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactQueryService
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactService
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [ArtifactController::class])
@AutoConfigureMockMvc
class ArtifactControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var artifactQueryService: ArtifactQueryService

    @MockkBean
    private lateinit var artifactService: ArtifactService

    @Test
    fun `getAllArtifacts uses default pagination and empty filter`() {
        every { artifactQueryService.getAllArtifacts(1, 20, "") } returns response()

        mockMvc
            .perform(
                get("/api/v1/admin/artifacts")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.items[0].metadata").value("""{"repositoryFullName":"owner/repo"}"""))
            .andExpect(jsonPath("$.items[0].ingestedAt").value("2026-01-02T03:04:05Z"))
            .andExpect(jsonPath("$.page.number").value(1))
            .andExpect(jsonPath("$.page.size").value(20))

        verify(exactly = 1) { artifactQueryService.getAllArtifacts(1, 20, "") }
    }

    @Test
    fun `getAllArtifacts forwards explicit pagination and filter`() {
        every { artifactQueryService.getAllArtifacts(2, 10, "github") } returns response()

        mockMvc
            .perform(
                get("/api/v1/admin/artifacts?page=2&size=10&filter=github")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))),
            ).andExpect(status().isOk)

        verify(exactly = 1) { artifactQueryService.getAllArtifacts(2, 10, "github") }
    }

    @Test
    fun `getArtifactContent returns bytes with content type`() {
        val projectId = UUID.randomUUID()
        val artifactId = UUID.randomUUID()
        every {
            artifactService.getArtifactContent(projectId, artifactId, "auth-user")
        } returns ArtifactContentResponse(
            content = byteArrayOf(1, 2, 3),
            mime = "image/png",
        )

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/artifacts/$artifactId/content")
                    .with(
                        jwt()
                            .jwt { it.subject("auth-user") }
                            .authorities(SimpleGrantedAuthority("ROLE_USER")),
                    ),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
            .andExpect(header().string("Content-Disposition", "inline"))
            .andExpect(content().bytes(byteArrayOf(1, 2, 3)))

        verify(exactly = 1) {
            artifactService.getArtifactContent(projectId, artifactId, "auth-user")
        }
    }

    @Test
    fun `getArtifactContent redirects to remote source url`() {
        val projectId = UUID.randomUUID()
        val artifactId = UUID.randomUUID()
        val sourceUrl = "https://github.com/owner/repo/blob/main/image.png"
        every {
            artifactService.getArtifactContent(projectId, artifactId, "auth-user")
        } returns ArtifactContentRedirectResponse(sourceUrl)

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/artifacts/$artifactId/content")
                    .with(
                        jwt()
                            .jwt { it.subject("auth-user") }
                            .authorities(SimpleGrantedAuthority("ROLE_USER")),
                    ),
            ).andExpect(status().isFound)
            .andExpect(redirectedUrl(sourceUrl))

        verify(exactly = 1) {
            artifactService.getArtifactContent(projectId, artifactId, "auth-user")
        }
    }

    private fun response() = ArtifactPageResponse(
        items = listOf(
            ArtifactResponse(
                id = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
                title = "README.md",
                sourceSystem = SourceSystem.GITHUB,
                sourceUrl = "https://github.com/owner/repo/blob/main/README.md",
                artifactType = ArtifactType.FILE,
                ingestedAt = Instant.parse("2026-01-02T03:04:05Z"),
                metadata = """{"repositoryFullName":"owner/repo"}""",
            ),
        ),
        page = PageMetadata(
            number = 1,
            size = 20,
            totalElements = 1,
            totalPages = 1,
            hasNext = false,
            hasPrevious = false,
        ),
    )
}
