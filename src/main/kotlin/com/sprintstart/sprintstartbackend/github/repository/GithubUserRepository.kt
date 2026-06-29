package com.sprintstart.sprintstartbackend.github.repository

import com.sprintstart.sprintstartbackend.github.models.GithubUser
import com.sprintstart.sprintstartbackend.github.models.GithubUserPat
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface GithubUserRepository : JpaRepository<GithubUser, GithubUserPat> {
    @Modifying
    @Query("UPDATE GithubUser u SET u.id.name = :newName WHERE u.id.authId = :authId AND u.id.name = :oldName")
    fun updatePatName(authId: String, oldName: String, newName: String): Int

    @Query("SELECT u.id.name FROM GithubUser u WHERE u.id.authId = :authId")
    fun findAllByAuthId(authId: String): List<String>
}
