package com.sprintstart.sprintstartbackend.ingestion.model.entity

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
 * How many of the lines a file has now one author wrote, as `git blame` said when the file was last read.
 *
 * This is evidence, not an artifact: it is derived from the repository, never shown on its own, and
 * rebuilt whenever the file is read again. There is one row per file and author, so the rows of a file
 * add up to its lines.
 *
 * @property id The identity of the row, assigned when the row is first saved.
 * @property repository The key of the repository the file lives in, for example `github:owner/repo`.
 * @property path The path of the file in the repository.
 * @property authorEmail The lower-cased email of the author, exactly as Git recorded it.
 * @property lines How many lines of the file the author wrote.
 */
@Entity
@Table(
    name = "file_authorship",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_file_authorship", columnNames = ["repository", "path", "author_email"]),
    ],
    indexes = [Index(name = "idx_file_authorship_repository", columnList = "repository")],
)
class FileAuthorship(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @Column(nullable = false)
    val repository: String,
    @Column(nullable = false, length = 2048)
    val path: String,
    @Column(name = "author_email", nullable = false)
    val authorEmail: String,
    @Column(nullable = false)
    val lines: Int,
)
