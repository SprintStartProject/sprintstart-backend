package com.sprintstart.sprintstartbackend.offboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

/**
 * A project manager's confirmation that one identity is another person's.
 *
 * Evidence names people by whatever the source recorded: a commit email, a GitHub login. One developer
 * usually has several, and a name alone is not a reliable match, so a link only exists once someone
 * confirmed it. A link belongs to one project, because the same email can be two people elsewhere.
 *
 * Links never chain: [person] is never itself the alias of another row, so reading one row is enough to
 * know who an identity counts as.
 *
 * @property id The row's identity, assigned when the row is first saved, so it is `null` only on a
 *           row that has not been stored yet.
 * @property projectId The project the link was confirmed for.
 * @property alias The identity folded into [person], as a lower-cased email or login.
 * @property person The identity the alias counts as, as a lower-cased email or login.
 */
@Entity
@Table(
    name = "person_alias",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_person_alias_project_alias",
            columnNames = ["project_id", "alias"],
        ),
    ],
    indexes = [Index(name = "idx_person_alias_project", columnList = "project_id")],
)
class PersonAlias(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    @Column(nullable = false)
    val alias: String,
    @Column(nullable = false)
    var person: String,
)
