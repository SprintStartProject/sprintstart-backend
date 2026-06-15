package com.sprintstart.sprintstartbackend.user.repository

import com.sprintstart.sprintstartbackend.user.model.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByAuthId(authId: String): Optional<User>

    fun existsByAuthId(authId: String): Boolean

    @Query("select u.id from User u where u.authId = :authId")
    fun findIdByAuthId(authId: String): Optional<UUID>
}
