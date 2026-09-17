package com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Accepts the tenant, selected space, a reference to a shared Atlassian credential, and optional
 * stable page-ID filters. Carries no secret material: [credentialName] is resolved against the
 * caller's already-stored Atlassian credential.
 */
class CreateConfluenceConnectionRequest(
    @field:NotBlank
    @field:Size(max = 2048)
    val baseUrl: String,
    @field:NotBlank
    @field:Pattern(regexp = "^[0-9]+$")
    val spaceId: String,
    @field:NotBlank
    @field:Size(max = 255)
    val credentialName: String,
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
            "baseUrl=$baseUrl, spaceId=$spaceId, credentialName=$credentialName, " +
            "pageAllowlist=$pageAllowlist, pageDenylist=$pageDenylist)"
    }
}
