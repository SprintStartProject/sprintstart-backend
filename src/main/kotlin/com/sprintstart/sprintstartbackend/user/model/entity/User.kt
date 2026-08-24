package com.sprintstart.sprintstartbackend.user.model.entity

import com.sprintstart.sprintstartbackend.user.external.enums.GithubLoginSource
import com.sprintstart.sprintstartbackend.user.external.enums.GithubLoginVerification
import com.sprintstart.sprintstartbackend.user.external.enums.Role
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "sprintstart_users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true, updatable = false)
    val authId: String,
    @Column(nullable = false)
    var username: String,
    @Column(nullable = true)
    var email: String?,
    @Column(nullable = false)
    var firstname: String,
    @Column(nullable = false)
    var lastname: String,
    @Column(nullable = false)
    var enabled: Boolean = true,
    @Column(nullable = true)
    var profileIcon: String? = null,
    // Restored alongside the new model rather than replaced by it: the blueprint-era onboarding
    // path still reads these, and this stack does not remove that feature.
    @Column(nullable = false)
    var hasCompletedOnboarding: Boolean = false,
    @OneToMany(
        mappedBy = "user",
        cascade = [jakarta.persistence.CascadeType.ALL],
        orphanRemoval = true,
    )
    @BatchSize(size = 50)
    var skillAssessments: MutableSet<UserSkillAssessment> = mutableSetOf(),
    // Stamped by SessionActivityService on authenticated request traffic; used to detect an idle
    // gap past the configured threshold as a stand-in for a real login/session boundary.
    @Column(name = "last_seen_at", nullable = true)
    var lastSeenAt: Instant? = null,
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "id")],
    )
    @Column(name = "role", nullable = false)
    val roles: MutableSet<Role> = mutableSetOf(),
    @Column(nullable = true)
    var avatarUrl: String? = null,
    // The GitHub account this user contributes as. Artifact verification attributes a submitted
    // pull request to a hire by comparing its author against this, so it is what makes the
    // highest-rigor tier attributable at all -- see GithubLoginSource for how far it can be
    // trusted. Null until the user (or a PM) fills it in.
    @Column(name = "github_login", nullable = true, unique = true)
    var githubLogin: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "github_login_source", nullable = true)
    var githubLoginSource: GithubLoginSource? = null,
    // Whether GitHub confirmed this account exists. Null means "not checked, or the check could not
    // run" -- an outage or a rate limit never records NOT_FOUND, because a wrong "no such account"
    // in front of somebody whose account is fine is worse than saying nothing. Cleared whenever the
    // login changes, so a verdict never outlives the value it was about.
    @Enumerated(EnumType.STRING)
    @Column(name = "github_login_verification", nullable = true)
    var githubLoginVerification: GithubLoginVerification? = null,
    @Column(name = "github_login_verified_at", nullable = true)
    var githubLoginVerifiedAt: Instant? = null,
    /**
     * The name this user appears under in Jira, exactly as Jira renders it.
     *
     * The Jira equivalent of [githubLogin], and the reason a non-developer's work can be *observed*
     * rather than attested: an issue assigned to this name and moved to Done by somebody else is
     * evidence nobody had to vouch for.
     *
     * A display name is a weaker key than a GitHub login, and knowingly so. It is the only
     * identity the ingested Jira data carries — the connector's `JiraAuthor` parses `displayName`,
     * `active`, `created` and `updated`, and drops Jira's `accountId` at parse time. Two consequences
     * follow, and only the first is defended here:
     *
     * - Two SprintStart users cannot claim the same name. Enforced by `JiraDisplayNameService`
     *   and by this column's uniqueness, exactly as for [githubLogin].
     * - A namesake inside Jira itself is undetectable. If two Jira accounts genuinely share a
     *   display name, nothing ingested tells them apart, so their work would merge into one person's
     *   record. Parsing `accountId` in the connector is the fix, and it is deliberately not done
     *   here: it edits upstream-owned code for a case nobody has hit.
     *
     * Stored as typed, not lower-cased — unlike a GitHub login, this is a human name that is
     * displayed back, and case-folding "de Vries" is how a name stops being somebody's.
     */
    @Column(name = "jira_display_name", nullable = true, unique = true)
    var jiraDisplayName: String? = null,
    // When the user opted in to having their existing work in the project's connected repositories
    // used to calibrate their skill assessment. Null means no consent -- the default, and what
    // revoking returns it to. Consent is the gate; the derived signal itself lives in
    // GithubHistoryPrior and is deleted on revocation.
    @Column(name = "github_seeding_consent_at", nullable = true)
    var githubSeedingConsentAt: Instant? = null,
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_projects",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "project_id")],
    )
    var projects: MutableSet<Project> = mutableSetOf(),
    /**
     * This user's project assignments, read-only from here.
     *
     * The inverse side of [ProjectUserAssignment.user], so nothing is ever written through it —
     * assignments are created and roled via `ProjectUserAssignment` itself. Roles live on the
     * assignment (scoped to the project), so the per-project and the whole-person questions are
     * both answered from this collection: read one assignment's roles for the former, the union
     * across all of them for the latter.
     */
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    val projectAssignments: MutableSet<ProjectUserAssignment> = mutableSetOf(),
)
