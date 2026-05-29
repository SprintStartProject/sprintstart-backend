package com.sprintstart.sprintstartbackend.upload.controller

import com.sprintstart.sprintstartbackend.upload.model.dto.UploadArtifactResponse
import com.sprintstart.sprintstartbackend.upload.model.dto.UploadListItemResponse
import com.sprintstart.sprintstartbackend.upload.service.UploadService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/v1/uploads")
class UploadController(
    private val uploadService: UploadService,
) {
    @Operation(summary = "Upload markdown artifacts")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Upload processed",
            ),
        ],
    )
    @PostMapping(
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    fun upload(
        @RequestPart("files")
        files: List<MultipartFile>,
        @RequestParam("uploaderId")
        uploaderId: UUID,
    ): ResponseEntity<List<UploadArtifactResponse>> {
        val response = uploadService.upload(
            files = files,
            uploaderId = uploaderId,
        )

        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun listUploads(
        @RequestParam uploaderId: UUID,
    ): ResponseEntity<List<UploadListItemResponse>> =
        ResponseEntity.ok(
            uploadService.listUploads(uploaderId)
        )
}
