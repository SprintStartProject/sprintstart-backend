package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.controller

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request.ConnectBitbucketRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.ConnectBitbucketRepositoryResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketConnectionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Exposes connecting a Bitbucket repository to the application. */
@Tag(name = "Bitbucket Connector", description = "Connect Bitbucket Cloud repositories.")
@RestController
@RequestMapping("/api/v1/bitbucket")
internal class BitbucketConnectionController(
    private val service: BitbucketConnectionService,
) {
    /**
     * Connects one Bitbucket repository using a credential of the authenticated user.
     *
     * The connection is stored and the repository is cloned asynchronously, so the response carries
     * the transaction id the connector's events correlate on rather than the repository state.
     * A credential that does not exist surfaces as 404 through the shared Atlassian credential
     * exception handler.
     */
    @Operation(
        summary = "Connect a Bitbucket repository",
        description = "Stores the connection and starts cloning the repository in the background.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Connection accepted",
                content = [Content(schema = Schema(implementation = ConnectBitbucketRepositoryResponse::class))],
            ),
            ApiResponse(responseCode = "400", description = "Request body is invalid"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "The named credential does not exist"),
        ],
    )
    @PostMapping
    suspend fun connect(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ConnectBitbucketRepositoryRequest,
    ): ResponseEntity<ConnectBitbucketRepositoryResponse> {
        val response = service.connectRepositoryIfExists(jwt.subject, request)
        return ResponseEntity.ok(response)
    }
}
