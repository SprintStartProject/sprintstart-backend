package com.sprintstart.sprintstartbackend.config

import com.sprintstart.sprintstartbackend.user.external.enums.Role

object KeycloakRoleMapper {
    private val realmRoleMapping = mapOf(
        "user" to Role.USER,
        "project-manager" to Role.PM,
        "human-resources" to Role.HR,
        "admin" to Role.ADMIN,
    )

    fun mapRealmRoles(realmRoles: Collection<String>): Set<Role> {
        return realmRoles
            .mapNotNull { realmRoleMapping[it] }
            .toSet()
    }
}
