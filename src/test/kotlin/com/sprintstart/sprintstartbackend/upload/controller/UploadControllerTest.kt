package com.sprintstart.sprintstartbackend.upload.controller

import com.sprintstart.sprintstartbackend.canonical.controller.UploadController
import com.sprintstart.sprintstartbackend.upload.model.dto.UploadArtifactResponse
import com.sprintstart.sprintstartbackend.upload.model.dto.UploadListItemResponse
import com.sprintstart.sprintstartbackend.upload.service.UploadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.multipart.MultipartFile
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
                eq(listOf(file)),
                eq(uploaderId),
            ),
        ).thenReturn(response)

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .multipart("/api/v1/uploads")
                    .file(file)
                    .param("uploaderId", uploaderId.toString()),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                MockMvcResultMatchers.jsonPath("$[0].filename").value("test.md"),
            ).andExpect(
                MockMvcResultMatchers.jsonPath("$[0].status").value("ok"),
            )

        val filesCaptor = argumentCaptor<List<MultipartFile>>()
        verify(uploadService).upload(filesCaptor.capture(), eq(uploaderId))

        assertEquals(1, filesCaptor.firstValue.size)
        assertEquals("test.md", filesCaptor.firstValue.first().originalFilename)
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

        whenever(uploadService.listUploads(eq(uploaderId))).thenReturn(uploads)

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/uploads")
                    .param("uploaderId", uploaderId.toString()),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                MockMvcResultMatchers.jsonPath("$[0].filename").value("doc.md"),
            ).andExpect(
                MockMvcResultMatchers.jsonPath("$[0].mime").value("text/markdown"),
            )

        verify(uploadService).listUploads(eq(uploaderId))
    }

    @Test
    fun `deleteUpload returns 204`() {
        val artifactId = UUID.randomUUID()

        mockMvc
            .perform(
                MockMvcRequestBuilders.delete("/api/v1/uploads/$artifactId"),
            ).andExpect(MockMvcResultMatchers.status().isNoContent)

        verify(uploadService).deleteUpload(eq(artifactId))
    }
}
