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
    /**
     * Uploads artifacts into a project as the authenticated user.
     *
     * The uploader id is resolved from the JWT subject and the service checks project access before
     * processing files, so clients cannot upload under another actor or into a foreign project.
     *
     * @param files Files to upload.
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param request Upload metadata containing the target project.
     * @return One response item per input file.
     */
    @Operation(summary = "Upload artifacts to a project")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Upload processed"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role or project access"),
            ApiResponse(responseCode = "404", description = "Authenticated user not found"),
        ],
    )
    @PostMapping(
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
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
        )

        return ResponseEntity.ok(response)
    }

    /**
     * Lists artifacts that belong to a project the authenticated user can access.
     *
     * Project access is checked in the service before upload metadata is returned.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param projectId Project whose artifacts should be listed.
     * @return Artifacts uploaded into the requested project.
     */
    @Operation(summary = "List project uploads")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Uploads returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role or project access"),
        ],
    )
    @GetMapping
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
    fun listUploads(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
        @RequestParam projectId: UUID,
    ): ResponseEntity<List<UploadListItemResponse>> =
        ResponseEntity.ok(
            uploadService.listUploads(
                authId = jwt.subject,
                projectId = projectId,
            ),
        )

    /**
     * Deletes artifacts from a project as the authenticated user.
     *
     * The remover id is resolved from the JWT subject. Artifact ids outside the requested project
     * are treated as missing and are not deleted.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param request Deletion metadata containing artifact ids and the target project.
     * @return No content when the deletion batch has been processed.
     */
    @Operation(summary = "Delete project uploads")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Deletion batch processed"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role or project access"),
            ApiResponse(responseCode = "404", description = "Authenticated user not found"),
        ],
    )
    @DeleteMapping(
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
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
            artifactIds = request.artifactIds,
            projectId = request.projectId,
        )

        return ResponseEntity
            .noContent()
            .build()
    }
}
