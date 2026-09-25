package com.sprintstart.sprintstartbackend.connectors.notion.model.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AddNotionCredentialRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:NotBlank
    @field:Size(max = 4096)
    val token: String,
) {
    override fun toString(): String = "AddNotionCredentialRequest(name=$name, token=<redacted>)"
}

data class ChangeNotionCredentialTokenRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    @field:NotBlank
    @field:Size(max = 4096)
    val newToken: String,
) {
    override fun toString(): String = "ChangeNotionCredentialTokenRequest(name=$name, newToken=<redacted>)"
}

data class ChangeNotionCredentialNameRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val oldName: String,
    @field:NotBlank
    @field:Size(max = 255)
    val newName: String,
)

data class DeleteNotionCredentialRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
)
