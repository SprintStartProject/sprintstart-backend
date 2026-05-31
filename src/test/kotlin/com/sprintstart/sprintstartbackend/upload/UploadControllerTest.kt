package com.sprintstart.sprintstartbackend.upload

import com.sprintstart.sprintstartbackend.upload.controller.UploadController
import com.sprintstart.sprintstartbackend.upload.model.dto.UploadArtifactResponse
import com.sprintstart.sprintstartbackend.upload.model.dto.UploadListItemResponse
import com.sprintstart.sprintstartbackend.upload.service.UploadService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(UploadController::class)
class UploadControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var uploadService: UploadService

    @Test
    fun `upload returns 200 with uploaded artifacts`() {
        val uploaderId = UUID.randomUUID()

        val file = MockMultipartFile(
            "files",
            "test.md",
            "text/markdown",
            "# hello".toByteArray(),
        )

        val response = listOf(
            UploadArtifactResponse(
                id = UUID.randomUUID(),
                filename = "test.md",
                status = "ok",
            ),
        )

        whenever(
            uploadService.upload(
                any(),
                eq(uploaderId),
            ),
        ).thenReturn(response)

        mockMvc
            .perform(
                multipart("/v1/uploads")
                    .file(file)
                    .param(
                        "uploaderId",
                        uploaderId.toString(),
                    ),
            ).andExpect(status().isOk)
            .andExpect(
                jsonPath("$[0].filename")
                    .value("test.md"),
            ).andExpect(
                jsonPath("$[0].status")
                    .value("ok"),
            )

        verify(uploadService).upload(
            any(),
            eq(uploaderId),
        )
    }

    @Test
    fun `listUploads returns uploader artifacts`() {
        val uploaderId = UUID.randomUUID()

        val uploads = listOf(
            UploadListItemResponse(
                id = UUID.randomUUID(),
                filename = "doc.md",
                mime = "text/markdown",
                uploadedAt = Instant.now(),
            ),
        )

        whenever(
            uploadService.listUploads(
                uploaderId,
            ),
        ).thenReturn(uploads)

        mockMvc
            .perform(
                get("/v1/uploads")
                    .param(
                        "uploaderId",
                        uploaderId.toString(),
                    ),
            ).andExpect(status().isOk)
            .andExpect(
                jsonPath("$[0].filename")
                    .value("doc.md"),
            ).andExpect(
                jsonPath("$[0].mime")
                    .value("text/markdown"),
            )

        verify(uploadService)
            .listUploads(uploaderId)
    }

    @Test
    fun `deleteUpload returns 204`() {
        val artifactId = UUID.randomUUID()

        mockMvc
            .perform(
                delete("/v1/uploads/$artifactId"),
            ).andExpect(status().isNoContent)

        verify(uploadService)
            .deleteUpload(artifactId)
    }
}
