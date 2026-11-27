package com.sprintstart.sprintstartbackend.ingestion.model.entity

import jakarta.persistence.Column
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
 * One saved version of a documentation page, with who saved it.
 *
 * This is evidence, not an artifact, like [FileChange] for commits: it is rebuilt from the page's version
 * history whenever the page is read and lives and dies with the page artifact. The page's title is not
 * copied here, so a renamed page keeps its whole history under its current title.
 *
 * @property id The row's identity, assigned when the row is first saved.
 * @property artifact The page artifact the version belongs to.
 * @property repository The key of the space the page lives in, for example `confluence:ARCH`.
 * @property versionNumber The version number, 1 for the version that created the page.
 * @property authorAccountId The account ID of the person who saved the version at the source.
 * @property editedAt When the version was saved.
 */
@Entity
@Table(
    name = "page_edit",
    indexes = [
        Index(name = "idx_page_edit_artifact", columnList = "artifact_id"),
        Index(name = "idx_page_edit_repository", columnList = "repository"),
    ],
)
class PageEdit(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artifact_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val artifact: Artifact,
    @Column(nullable = false)
    val repository: String,
    @Column(name = "version_number", nullable = false)
    val versionNumber: Int,
    @Column(name = "author_account_id", nullable = false)
    val authorAccountId: String,
    @Column(name = "edited_at", nullable = false)
    val editedAt: Instant,
)
