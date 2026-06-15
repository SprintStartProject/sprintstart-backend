package com.sprintstart.sprintstartbackend.upload.controller

import com.sprintstart.sprintstartbackend.upload.model.dto.UploadArtifactResponse
import com.sprintstart.sprintstartbackend.upload.model.dto.UploadListItemResponse
import com.sprintstart.sprintstartbackend.upload.service.UploadService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/uploads")
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
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
        ],
    )
    @PostMapping(
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    @PreAuthorize("hasRole('USER')")
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

    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Upload processed"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
        ],
    )
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    fun listUploads(
        @RequestParam uploaderId: UUID,
    ): ResponseEntity<List<UploadListItemResponse>> =
        ResponseEntity.ok(
            uploadService.listUploads(uploaderId),
        )

    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Deleted Artifact"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
        ],
    )
    @DeleteMapping("/{artifactId}")
    @PreAuthorize("hasRole('USER')")
    fun deleteUpload(
        @PathVariable artifactId: UUID,
    ): ResponseEntity<Void> {
        uploadService.deleteUpload(
            artifactId,
        )

        return ResponseEntity
            .noContent()
            .build()
    }
}
