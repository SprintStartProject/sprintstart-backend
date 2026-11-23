package com.sprintstart.sprintstartbackend.ingestion.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.Instant
import java.util.UUID

/**
 * One file one commit changed, with who changed it and by how much.
 *
 * This is evidence, not an artifact: it is never indexed or shown on its own, and it can be rebuilt
 * at any time by reading the repository's history again. It is a table of its own rather than a
 * field of the commit artifact's metadata because it is queried by repository, path and person
 * across the whole history, which a text column cannot do with an index.
 *
 * The row lives and dies with its commit artifact. Project membership is not copied here: the
 * artifact already carries it, so a project that loses a repository loses the evidence with it.
 *
 * @property id The row's identity, assigned when the row is first saved, so it is `null` only on a
 *           row that has not been stored yet.
 * @property artifact The commit artifact the change belongs to.
 * @property repository The key of the repository the commit was read from, for example
 *           `github:owner/repo`, so a path is only ever compared within its own repository.
 * @property path The file's path after the commit.
 * @property renamedFrom The path the file had before the commit when the commit moved it, `null`
 *           when it kept its path.
 * @property author Who made the commit.
 * @property changedAt When the commit was made.
 * @property lines How much of the file the commit changed.
 */
@Entity
@Table(
    name = "file_change",
    indexes = [
        Index(name = "idx_file_change_artifact", columnList = "artifact_id"),
        Index(name = "idx_file_change_repository", columnList = "repository"),
    ],
)
class FileChange(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artifact_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val artifact: Artifact,
    @Column(nullable = false)
    val repository: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val path: String,
    @Column(name = "renamed_from", columnDefinition = "TEXT")
    val renamedFrom: String?,
    @Embedded
    val author: ChangeAuthor,
    @Column(name = "changed_at", nullable = false)
    val changedAt: Instant,
    @Embedded
    val lines: LineCounts,
)

/**
 * The author recorded in a commit.
 *
 * Both values are free text chosen by the committer. The email is the only part that can later be
 * matched to an account, for example through a provider's no-reply address.
 *
 * @property name The Git author name.
 * @property email The Git author email.
 */
@Embeddable
class ChangeAuthor(
    @Column(name = "author_name", nullable = false)
    val name: String,
    @Column(name = "author_email", nullable = false)
    val email: String,
)

/**
 * How many lines a commit added to and removed from one file.
 *
 * Both are zero for a binary file, which Git reports no line counts for.
 *
 * @property added Lines added.
 * @property removed Lines removed.
 */
@Embeddable
class LineCounts(
    @Column(name = "lines_added", nullable = false)
    val added: Int,
    @Column(name = "lines_removed", nullable = false)
    val removed: Int,
)
