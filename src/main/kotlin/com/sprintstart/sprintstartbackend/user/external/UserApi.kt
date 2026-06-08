package com.sprintstart.sprintstartbackend.user.external

import java.util.UUID

interface UserApi {
    fun exists(id: UUID): Boolean

    fun existsByAuthId(authId: String): Boolean
}
