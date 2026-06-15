package com.sprintstart.sprintstartbackend.user.external

import java.util.Optional
import java.util.UUID

interface UserApi {
    fun exists(id: UUID): Boolean

    fun getUserIdByAuthId(authId: String): Optional<UUID>
}
