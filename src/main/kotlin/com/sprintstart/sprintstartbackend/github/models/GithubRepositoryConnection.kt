package com.sprintstart.sprintstartbackend.github.models

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "gh_repository_connections")
data class GithubRepositoryConnection(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var owner: String,
    @Column(nullable = false)
    var name: String,
    @JoinColumn(name = "snapshot_id")
    @OneToOne(
        cascade = [CascadeType.ALL],
        fetch = FetchType.LAZY,
    )
    var snapshot: GithubRepositorySnapshot? = null,
)
