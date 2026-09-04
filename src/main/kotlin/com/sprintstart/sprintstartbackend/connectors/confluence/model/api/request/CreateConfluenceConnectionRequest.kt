package com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** Accepts the tenant, selected space, credentials, and optional stable page-ID filters. */
class CreateConfluenceConnectionRequest(
    @field:NotBlank
    @field:Size(max = 2048)
    val baseUrl: String,
    @field:NotBlank
    @field:Pattern(regexp = "^[0-9]+$")
    val spaceId: String,
    @field:NotBlank
    @field:Email
    @field:Size(max = 320)
    @field:Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
    val email: String,
    @field:NotBlank
    @field:Size(max = 4096)
    @field:Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
    val apiToken: String,
    @field:Size(max = 1000)
    val pageAllowlist: List<
        @NotBlank
        @Size(max = 255)
        String,
    > = emptyList(),
    @field:Size(max = 1000)
    val pageDenylist: List<
        @NotBlank
        @Size(max = 255)
        String,
    > = emptyList(),
) {
    override fun toString(): String {
        return "CreateConfluenceConnectionRequest(" +
            "baseUrl=$baseUrl, spaceId=$spaceId, email=<redacted>, apiToken=<redacted>, " +
            "pageAllowlist=$pageAllowlist, pageDenylist=$pageDenylist)"
    }
}
