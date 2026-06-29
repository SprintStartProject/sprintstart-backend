package com.sprintstart.sprintstartbackend.github.models

import com.sprintstart.sprintstartbackend.shared.crypto.SymmetricEncryptedStringConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

@Entity
@Table(name = "gh_user")
class GithubUser(
    @EmbeddedId
    var id: GithubUserPat,
    @Convert(converter = SymmetricEncryptedStringConverter::class)
    @Column(nullable = false, unique = true)
    var token: String = "",
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "last_used_at", nullable = false)
    var lastUsedAt: Instant = Instant.now(),
    @OneToMany(mappedBy = "user")
    var repositories: MutableList<GithubRepositoryConnection> = mutableListOf(),
)

@Embeddable
data class GithubUserPat(
    @Column(name = "auth_id", nullable = false)
    var authId: String = "",
    @Column(nullable = false, unique = true)
    var name: String,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
