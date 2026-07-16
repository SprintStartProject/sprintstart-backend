package com.sprintstart.sprintstartbackend.ingestion.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactPageResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.PageMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactQueryService
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactService
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [ArtifactController::class])
@AutoConfigureMockMvc(addFilters = false)
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
            .perform(get("/api/v1/admin/artifacts"))
            .andExpect(status().isOk)
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
            .perform(get("/api/v1/admin/artifacts?page=2&size=10&filter=github"))
            .andExpect(status().isOk)

        verify(exactly = 1) { artifactQueryService.getAllArtifacts(2, 10, "github") }
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
