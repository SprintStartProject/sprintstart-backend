package com.sprintstart.sprintstartbackend.github.models

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "gh_repository_connections")
class GithubRepositoryConnection(
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
    @OneToOne(mappedBy = "repository", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var snapshot: GithubRepositorySnapshot? = null,
    @OneToMany(mappedBy = "repository", orphanRemoval = true)
    var filesSnapshots: MutableList<GithubFileSnapshot> = mutableListOf(),
)
