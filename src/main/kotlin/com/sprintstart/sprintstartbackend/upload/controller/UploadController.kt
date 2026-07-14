package com.sprintstart.sprintstartbackend.upload.controller

import com.sprintstart.sprintstartbackend.upload.model.dto.request.DeleteArtifactsRequest
import com.sprintstart.sprintstartbackend.upload.model.dto.request.UploadArtifactsRequest
import com.sprintstart.sprintstartbackend.upload.model.dto.response.UploadArtifactResponse
import com.sprintstart.sprintstartbackend.upload.model.dto.response.UploadListItemResponse
import com.sprintstart.sprintstartbackend.upload.service.UploadService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
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
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
        @Valid
        @RequestPart("request")
        request: UploadArtifactsRequest,
    ): ResponseEntity<List<UploadArtifactResponse>> {
        val response = uploadService.upload(
            authId = jwt.subject,
            files = files,
            projectId = request.projectId,
            uploaderId = request.uploaderId,
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
    fun deleteUploads(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
        @Valid
        @RequestPart("request")
        request: DeleteArtifactsRequest,
    ): ResponseEntity<Void> {
        uploadService.deleteUpload(
            authId = jwt.subject,
            request.artifactIds,
            request.removerId,
            request.projectId,
        )

        return ResponseEntity
            .noContent()
            .build()
    }
}
