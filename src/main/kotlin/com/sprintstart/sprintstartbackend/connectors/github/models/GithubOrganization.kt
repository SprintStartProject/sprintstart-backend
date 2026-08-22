package com.sprintstart.sprintstartbackend.connectors.github.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Records that organization-level metadata for a GitHub organization has been fetched.
 *
 * The login is the natural primary key and the row is written only after a successful fetch, so
 * `existsById` doubles as the "already connected" guard that prevents re-fetching on every
 * repository connect for the same organization.
 */
@Entity
@Table(name = "gh_organizations")
class GithubOrganization(
    @Id
    @Column(name = "login", nullable = false)
    var login: String,
    var name: String? = null,
)
