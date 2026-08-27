package com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

/** Accepts the tenant, selected space, credentials, and optional stable page-ID filters. */
class CreateConfluenceConnectionRequest(
    @field:NotBlank
    val baseUrl: String,
    @field:NotBlank
    val spaceId: String,
    @field:NotBlank
    @field:Email
    @field:Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
    val email: String,
    @field:NotBlank
    @field:Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
    val apiToken: String,
    val pageAllowlist: List<String> = emptyList(),
    val pageDenylist: List<String> = emptyList(),
) {
    override fun toString(): String {
        return "CreateConfluenceConnectionRequest(" +
            "baseUrl=$baseUrl, spaceId=$spaceId, email=<redacted>, apiToken=<redacted>, " +
            "pageAllowlist=$pageAllowlist, pageDenylist=$pageDenylist)"
    }
}
