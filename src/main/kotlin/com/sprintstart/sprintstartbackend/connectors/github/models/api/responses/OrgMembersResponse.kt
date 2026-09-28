package com.sprintstart.sprintstartbackend.connectors.github.models.api.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrgMembersResponse(
    val members: List<OrgMemberResponse>,
)

@Serializable
data class OrgMemberResponse(
    val login: String,
    @SerialName("html_url")
    val url: String,
)
