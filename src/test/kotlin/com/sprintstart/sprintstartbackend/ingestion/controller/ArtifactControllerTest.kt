package com.sprintstart.sprintstartbackend.ingestion.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactFilterCriteria
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSort
import com.sprintstart.sprintstartbackend.ingestion.model.dto.UploadFormat
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactContentRedirectResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactContentResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactFacetsResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactPageResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.FacetCountResponse
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
import java.time.LocalDate
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
            .andExpect(jsonPath("$.items[0].lastChangedAt").value("2026-01-09T03:04:05Z"))
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

    @Test
    fun `getProjectArtifacts forwards criteria with repeatable params and pagination`() {
        val projectId = UUID.randomUUID()
        val criteria = ArtifactFilterCriteria(
            search = "test",
            types = setOf(ArtifactType.FILE, ArtifactType.ISSUE),
            sources = setOf(SourceSystem.GITHUB),
            repositories = setOf("owner/repo"),
            format = UploadFormat.PDF,
        )
        every {
            artifactQueryService.getProjectArtifacts(1, 20, criteria, ArtifactSort.ADDED_DESC, projectId, "auth-user")
        } returns response()

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/artifacts")
                    .param("page", "1")
                    .param("size", "20")
                    .param("search", "test")
                    .param("types", "FILE", "ISSUE")
                    .param("sources", "GITHUB")
                    .param("repositories", "owner/repo")
                    .param("format", "PDF")
                    .with(
                        jwt()
                            .jwt { it.subject("auth-user") }
                            .authorities(SimpleGrantedAuthority("ROLE_USER")),
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].title").value("README.md"))

        verify(exactly = 1) {
            artifactQueryService.getProjectArtifacts(1, 20, criteria, ArtifactSort.ADDED_DESC, projectId, "auth-user")
        }
    }

    @Test
    fun `getProjectArtifacts binds an explicit sort`() {
        val projectId = UUID.randomUUID()
        every {
            artifactQueryService.getProjectArtifacts(
                1,
                20,
                ArtifactFilterCriteria(),
                ArtifactSort.CHANGED_DESC,
                projectId,
                "auth-user",
            )
        } returns response()

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/artifacts")
                    .param("sort", "CHANGED_DESC")
                    .with(userJwt()),
            ).andExpect(status().isOk)

        verify(exactly = 1) {
            artifactQueryService.getProjectArtifacts(
                1,
                20,
                ArtifactFilterCriteria(),
                ArtifactSort.CHANGED_DESC,
                projectId,
                "auth-user",
            )
        }
    }

    @Test
    fun `getProjectArtifacts rejects an unknown sort with 400`() {
        val projectId = UUID.randomUUID()

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/artifacts")
                    .param("sort", "RANDOM")
                    .with(userJwt()),
            ).andExpect(status().isBadRequest)

        verify(exactly = 0) {
            artifactQueryService.getProjectArtifacts(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `getProjectArtifacts binds from and to as ISO calendar days`() {
        val projectId = UUID.randomUUID()
        val criteria = ArtifactFilterCriteria(from = LocalDate.of(2026, 3, 1), to = LocalDate.of(2026, 3, 31))
        every {
            artifactQueryService.getProjectArtifacts(1, 20, criteria, ArtifactSort.ADDED_DESC, projectId, "auth-user")
        } returns response()

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/artifacts")
                    .param("from", "2026-03-01")
                    .param("to", "2026-03-31")
                    .with(userJwt()),
            ).andExpect(status().isOk)

        verify(exactly = 1) {
            artifactQueryService.getProjectArtifacts(1, 20, criteria, ArtifactSort.ADDED_DESC, projectId, "auth-user")
        }
    }

    @Test
    fun `getProjectArtifacts rejects a malformed date with 400`() {
        val projectId = UUID.randomUUID()

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/artifacts")
                    .param("from", "01.03.2026")
                    .with(userJwt()),
            ).andExpect(status().isBadRequest)

        verify(exactly = 0) {
            artifactQueryService.getProjectArtifacts(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `getProjectArtifactFacets binds the same date window as the list`() {
        val projectId = UUID.randomUUID()
        val criteria = ArtifactFilterCriteria(from = LocalDate.of(2026, 3, 1), to = LocalDate.of(2026, 3, 1))
        every {
            artifactQueryService.getProjectArtifactFacets(projectId, criteria, "auth-user")
        } returns facets()

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/artifacts/facets")
                    .param("from", "2026-03-01")
                    .param("to", "2026-03-01")
                    .with(userJwt()),
            ).andExpect(status().isOk)

        verify(exactly = 1) {
            artifactQueryService.getProjectArtifactFacets(projectId, criteria, "auth-user")
        }
    }

    @Test
    fun `getProjectArtifactFacets returns facet counts and never binds to single artifact route`() {
        val projectId = UUID.randomUUID()
        val facets = ArtifactFacetsResponse(
            types = listOf(FacetCountResponse("FILE", 10)),
            sources = listOf(FacetCountResponse("GITHUB", 10)),
            formats = listOf(FacetCountResponse("PDF", 2)),
            repositories = listOf(FacetCountResponse("owner/repo", 8)),
        )
        every {
            artifactQueryService.getProjectArtifactFacets(projectId, any(), "auth-user")
        } returns facets

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/artifacts/facets")
                    .with(
                        jwt()
                            .jwt { it.subject("auth-user") }
                            .authorities(SimpleGrantedAuthority("ROLE_USER")),
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.types[0].value").value("FILE"))
            .andExpect(jsonPath("$.types[0].count").value(10))
            .andExpect(jsonPath("$.sources[0].value").value("GITHUB"))
            .andExpect(jsonPath("$.formats[0].value").value("PDF"))
            .andExpect(jsonPath("$.repositories[0].value").value("owner/repo"))

        verify(exactly = 1) {
            artifactQueryService.getProjectArtifactFacets(projectId, any(), "auth-user")
        }
        verify(exactly = 0) {
            artifactQueryService.getArtifact(any(), any(), any())
        }
    }

    @Test
    fun `getArtifact returns single artifact when found`() {
        val projectId = UUID.randomUUID()
        val artifactId = UUID.randomUUID()
        val artifactResponse = response().items.single().copy(id = artifactId)
        every {
            artifactQueryService.getArtifact(projectId, artifactId, "auth-user")
        } returns artifactResponse

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/artifacts/$artifactId")
                    .with(
                        jwt()
                            .jwt { it.subject("auth-user") }
                            .authorities(SimpleGrantedAuthority("ROLE_USER")),
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(artifactId.toString()))
            .andExpect(jsonPath("$.title").value("README.md"))

        verify(exactly = 1) {
            artifactQueryService.getArtifact(projectId, artifactId, "auth-user")
        }
    }

    @Test
    fun `getArtifact returns 404 when artifact not found in project`() {
        val projectId = UUID.randomUUID()
        val artifactId = UUID.randomUUID()
        every {
            artifactQueryService.getArtifact(projectId, artifactId, "auth-user")
        } throws org.springframework.web.server
            .ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/artifacts/$artifactId")
                    .with(
                        jwt()
                            .jwt { it.subject("auth-user") }
                            .authorities(SimpleGrantedAuthority("ROLE_USER")),
                    ),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) {
            artifactQueryService.getArtifact(projectId, artifactId, "auth-user")
        }
    }

    private fun response() = ArtifactPageResponse(
        items = listOf(
            ArtifactResponse(
                id = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
                title = "README.md",
                sourceSystem = SourceSystem.GITHUB,
                sourceId = "12345",
                sourceUrl = "https://github.com/owner/repo/blob/main/README.md",
                artifactType = ArtifactType.FILE,
                ingestedAt = Instant.parse("2026-01-02T03:04:05Z"),
                lastChangedAt = Instant.parse("2026-01-09T03:04:05Z"),
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

    private fun userJwt() = jwt()
        .jwt { it.subject("auth-user") }
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    private fun facets() = ArtifactFacetsResponse(
        types = emptyList(),
        sources = emptyList(),
        formats = emptyList(),
        repositories = emptyList(),
    )
}
