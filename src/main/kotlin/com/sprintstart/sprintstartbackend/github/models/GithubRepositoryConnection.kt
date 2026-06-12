package com.sprintstart.sprintstartbackend.github.models

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "gh_repository_connections")
data class GithubRepositoryConnection(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var owner: String,
    @Column(nullable = false)
    var name: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ConnectionStatus = ConnectionStatus.UP_TO_DATE,
    @Column(name = "last_sha", nullable = false)
    var lastSha: String = "",
    @OneToOne(mappedBy = "repository", fetch = FetchType.LAZY)
    var snapshot: GithubRepositorySnapshot? = null,
    @OneToMany(mappedBy = "repository", orphanRemoval = true)
    var filesSnapshots: MutableList<GithubFileSnapshot> = mutableListOf(),
)
