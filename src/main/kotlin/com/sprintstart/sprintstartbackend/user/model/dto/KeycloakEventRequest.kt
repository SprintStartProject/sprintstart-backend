package com.sprintstart.sprintstartbackend.user.model.dto

data class KeycloakEventRequest(
    val source: String,
    val resourceType: String,
    val eventType: String,
    val realmId: String,
    val authId: String,
    val username: String?,
    val email: String?,
    val firstName: String?,
    val lastName: String?,
    val realmRoles: Set<String> = emptySet(),
)
