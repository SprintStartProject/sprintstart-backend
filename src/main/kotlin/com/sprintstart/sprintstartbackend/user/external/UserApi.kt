package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.Optional
import java.util.UUID

/**
 * Exported user-module API for other backend modules.
 *
 * Other modules should depend on this interface instead of calling user-module services
 * or repositories directly.
 */
interface UserApi {
    /**
     * Checks whether a user projection exists for the given SprintStart user ID.
     *
     * @param id Internal SprintStart user identifier.
     * @return `true` when the user exists, otherwise `false`.
     */
    fun exists(id: UUID): Boolean

    /**
     * Resolves the internal SprintStart user ID for a Keycloak authentication subject.
     *
     * @param authId External authentication identifier from Keycloak.
     * @return The matching user ID when present.
     */
    fun getUserIdByAuthId(authId: String): Optional<UUID>

    fun searchUsers(
        search: String?,
        roleIds: List<UUID>?,
        projectIds: List<UUID>?,
        pageable: Pageable,
    ): Page<UserDto>

    fun getUsersByIds(ids: List<UUID>): List<UserDto>
}
