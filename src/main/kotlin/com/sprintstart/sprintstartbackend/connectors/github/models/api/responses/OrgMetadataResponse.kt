package com.sprintstart.sprintstartbackend.connectors.github.models.api.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrgMetadataResponse(
    val login: String,
    val name: String? = null,
    val description: String? = null,
    val company: String? = null,
    val blog: String? = null,
    val location: String? = null,
    val email: String? = null,
    @SerialName("public_repos")
    val publicRepos: Int? = null,
    @SerialName("total_private_repos")
    val privateRepos: Int? = null,
)
