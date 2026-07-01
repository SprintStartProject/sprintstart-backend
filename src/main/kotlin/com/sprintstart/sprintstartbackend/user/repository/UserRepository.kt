package com.sprintstart.sprintstartbackend.user.repository

import com.sprintstart.sprintstartbackend.user.model.entity.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.Optional
import java.util.UUID

interface UserRepository :
    JpaRepository<User, UUID>,
    JpaSpecificationExecutor<User> {
    fun findByAuthId(authId: String): Optional<User>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u left join fetch u.roles where u.authId = :authId")
    fun findLockedByAuthId(authId: String): Optional<User>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u left join fetch u.roles where u.id = :id")
    fun findLockedById(id: UUID): Optional<User>

    fun existsByAuthId(authId: String): Boolean

    @Query("select u.id from User u where u.authId = :authId")
    fun findIdByAuthId(authId: String): Optional<UUID>

    @Query("select u.authId from User u where u.id = :id")
    fun findAuthIdById(id: UUID): Optional<String>

    @Modifying
    @Query(value = "delete from user_roles where id = :id", nativeQuery = true)
    fun deleteRolesByUserId(id: UUID): Int

    @Modifying
    @Query(value = "delete from sprintstart_users where id = :id", nativeQuery = true)
    fun deleteProjectionById(id: UUID): Int
}
