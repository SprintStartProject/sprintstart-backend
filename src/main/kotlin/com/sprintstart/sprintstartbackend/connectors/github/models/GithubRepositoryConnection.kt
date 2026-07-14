package com.sprintstart.sprintstartbackend.connectors.github.models

import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinColumns
import jakarta.persistence.ManyToOne
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
    @ElementCollection
    @CollectionTable(
        name = "gh_repository_connection_projects",
        joinColumns = [JoinColumn(name = "repository_connection_id")],
    )
    @Column(name = "project_id", nullable = false)
    private val projectIdsInternal: MutableSet<UUID> = mutableSetOf(),
    @ManyToOne
    @JoinColumns(
        JoinColumn(name = "user_auth_id", referencedColumnName = "auth_id", nullable = false),
        JoinColumn(name = "user_pat_name", referencedColumnName = "name", nullable = false),
    )
    var user: GithubUser,
    @Enumerated(EnumType.STRING)
    @Column(name = "connection_state", nullable = false)
    var connectionState: ConnectionState = ConnectionState.UP_TO_DATE,
    @Column(name = "source_enabled", nullable = false)
    var sourceEnabled: Boolean = false,
    @Column(name = "last_sha", nullable = false)
    var lastSha: String = "",
    @OneToOne(mappedBy = "repository", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var snapshot: GithubRepositorySnapshot? = null,
    @OneToMany(mappedBy = "repository", fetch = FetchType.LAZY, orphanRemoval = true)
    var filesSnapshots: MutableList<GithubFileSnapshot> = mutableListOf(),
) {
    val projectIds: Set<UUID>
        get() = projectIdsInternal.toSet()
}
