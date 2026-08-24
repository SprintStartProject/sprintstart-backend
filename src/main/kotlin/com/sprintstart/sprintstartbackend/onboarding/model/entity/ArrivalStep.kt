package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One thing that has to be true before a hire can work — an account, an access grant, a machine
 * that builds.
 *
 * It is not a gate. Nothing refuses to serve a hire because an arrival step is
 * outstanding: "not settled" is a value in a response body, never a 403.
 *
 * It is not per-hire content: one shared definition, with per-hire state beside it
 * ([ArrivalStepState]).
 *
 * [projectId] is nullable and null means company-wide, not "no scope". A hire's list is
 * company steps plus the steps of the projects they are on, deduplicated by [key] with a
 * project-scoped definition winning — so a project can sharpen a company step's wording without
 * forking the key its state is stored against.
 *
 * Uniqueness is enforced twice. A [key] must be unique within its scope, but `NULL` does
 * not conflict with `NULL` in Postgres, so a single unique index on `(key, project_id)` would
 * constrain project-scoped rows and silently permit unlimited duplicate company-wide ones. The
 * migration declares two partial unique indexes; Hibernate cannot express a partial index at
 * all and the test suite builds its schema from these entities, so
 * [com.sprintstart.sprintstartbackend.onboarding.service.ArrivalStepService] enforces the same rule
 * in code. The index protects the database, the service protects the tests.
 */
@Entity
@Table(name = "arrival_steps")
class ArrivalStep(
    @Id
    val id: UUID = UUID.randomUUID(),
    /**
     * The stable identifier this step is known by, immutable once created.
     *
     * [ArrivalStepState] points at this string rather than at [id], so a definition can be
     * deleted and re-added without destroying what a hire already settled. Changing a key is
     * rejected rather than cascaded.
     *
     * The column name is backticked because `key` is a reserved word in several dialects (e.g.
     * H2); that tells Hibernate to emit a dialect-appropriate quoted identifier.
     */
    @Column(name = "`key`", nullable = false)
    val key: String,
    /** Null means company-wide; a value scopes the step to one project. */
    @Column(name = "project_id", nullable = true)
    val projectId: UUID? = null,
    @Column(nullable = false)
    var title: String,
    @Column(nullable = true, columnDefinition = "TEXT")
    var description: String? = null,
    /** Where to go to actually do it — a request form, an internal wiki page. Optional. */
    @Column(nullable = true)
    var href: String? = null,
    /** Ordering within the step's own scope. Company steps and project steps are ordered separately. */
    @Column(nullable = false)
    var position: Int = 0,
    /**
     * How this step is settled: observed by the system, or declared by the hire. [Rigor.ATTESTED]
     * is an unused slot here.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "settled_by", nullable = false)
    var settledBy: Rigor = Rigor.DECLARED,
    /**
     * Whether the hire may settle this step by saying so.
     *
     * Not a synonym for [settledBy]. "Your machine builds" is observable but never
     * *refutable* — no contribution yet says nothing about the environment — so it is derived
     * and self-confirmable. "You have a GitHub account we can attribute work to" is the
     * opposite: the check is definitive, so letting somebody assert it would let them declare away
     * the one thing their work being credited depends on.
     *
     * Defaults to true: a step that nothing observes and nobody may tick can never be settled
     * at all.
     */
    @Column(name = "self_confirmable", nullable = false)
    var selfConfirmable: Boolean = true,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
)
