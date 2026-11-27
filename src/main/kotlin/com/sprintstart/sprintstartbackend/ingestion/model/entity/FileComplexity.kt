package com.sprintstart.sprintstartbackend.ingestion.model.entity

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileKind
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * How complicated the current version of a file is, measured when the file was last read.
 *
 * This is evidence, not an artifact: it is derived from the text of the file, never shown on its own,
 * and rebuilt whenever the file is read again. It is kept apart from the file artifact so the risk
 * analysis can read it for a whole repository without loading any file text.
 *
 * @property id The identity of the row, assigned when the row is first saved.
 * @property repository The key of the repository the file lives in, for example `github:owner/repo`.
 * @property path The path of the file in the repository.
 * @property indentation The sum of the indentation levels of the non-blank lines of the file.
 * @property lines The number of non-blank lines of the file.
 * @property kind What the file holds; `null` for a file measured before kinds were told apart.
 * @property complexity How much the file holds, in the unit of its [kind]; `null` when [kind] is.
 * @property measuredAt When the file was measured.
 */
@Entity
@Table(
    name = "file_complexity",
    uniqueConstraints = [UniqueConstraint(name = "uq_file_complexity_file", columnNames = ["repository", "path"])],
    indexes = [Index(name = "idx_file_complexity_repository", columnList = "repository")],
)
class FileComplexity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @Column(nullable = false)
    val repository: String,
    @Column(nullable = false, length = 2048)
    val path: String,
    @Column(nullable = false)
    var indentation: Double,
    @Column(nullable = false)
    var lines: Int,
    @Column(name = "measured_at", nullable = false)
    var measuredAt: Instant,
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    var kind: FileKind? = null,
    var complexity: Double? = null,
)
