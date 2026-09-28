package com.sprintstart.sprintstartbackend.connectors.notion.controller

import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.AddNotionCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.ChangeNotionCredentialNameRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.ChangeNotionCredentialTokenRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.DeleteNotionCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionCredentialResponse
import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionCredentialService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Notion Credentials", description = "Manage per-user Notion personal access tokens.")
@Validated
@RestController
@RequestMapping("/api/v1/notion/credentials")
@PreAuthorize("hasAnyRole('PM', 'ADMIN')")
internal class NotionCredentialController(
    private val credentialService: NotionCredentialService,
) {
    @Operation(summary = "List Notion credentials")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Credentials retrieved"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "PM or Admin role required"),
        ],
    )
    @GetMapping
    fun getCredentials(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<List<NotionCredentialResponse>> {
        return ResponseEntity.ok(credentialService.getCredentials(jwt.subject))
    }

    @Operation(summary = "Add a Notion credential")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Credential validated and created"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Authentication required or invalid Notion token"),
            ApiResponse(responseCode = "403", description = "PM or Admin role required"),
            ApiResponse(responseCode = "409", description = "Credential name already exists"),
            ApiResponse(responseCode = "502", description = "Notion validation failed"),
        ],
    )
    @PostMapping
    suspend fun addCredential(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: AddNotionCredentialRequest,
    ): ResponseEntity<NotionCredentialResponse> {
        val response = credentialService.addCredential(jwt.subject, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(summary = "Replace a Notion credential token")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Token validated and replaced"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Authentication required or invalid Notion token"),
            ApiResponse(responseCode = "403", description = "PM or Admin role required"),
            ApiResponse(responseCode = "404", description = "Credential not found"),
            ApiResponse(responseCode = "502", description = "Notion validation failed"),
        ],
    )
    @PutMapping("/token")
    suspend fun changeToken(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ChangeNotionCredentialTokenRequest,
    ): ResponseEntity<NotionCredentialResponse> {
        val response = credentialService.changeToken(
            authId = jwt.subject,
            request = request
        )
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Rename a Notion credential")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Credential renamed"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "PM or Admin role required"),
            ApiResponse(responseCode = "404", description = "Credential not found"),
            ApiResponse(responseCode = "409", description = "New credential name already exists"),
        ],
    )
    @PutMapping("/name")
    fun changeName(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ChangeNotionCredentialNameRequest,
    ): ResponseEntity<NotionCredentialResponse> {
        val response = credentialService.changeName(jwt.subject, request)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Delete a Notion credential")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Credential deleted"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "PM or Admin role required"),
            ApiResponse(responseCode = "404", description = "Credential not found"),
            ApiResponse(responseCode = "409", description = "Credential is still used by a page connection"),
        ],
    )
    @DeleteMapping
    fun deleteCredential(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: DeleteNotionCredentialRequest,
    ): ResponseEntity<Unit> {
        credentialService.deleteCredential(jwt.subject, request)
        return ResponseEntity.noContent().build()
    }
}
