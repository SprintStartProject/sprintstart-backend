package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.Optional
import java.util.UUID

interface UserApi {
    fun exists(id: UUID): Boolean

    fun getUserIdByAuthId(authId: String): Optional<UUID>

    fun searchUsers(
        search: String?,
        roleIds: List<UUID>?,
        projectIds: List<UUID>?,
        pageable: Pageable,
    ): Page<UserDto>

    fun getUsersByIds(ids: List<UUID>): List<UserDto>
}
