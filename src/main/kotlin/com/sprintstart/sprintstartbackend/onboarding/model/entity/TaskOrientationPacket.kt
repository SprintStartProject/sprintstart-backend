package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationOrigin
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * What the project already says about doing one task, assembled and cached.
 *
 * Deliberately the opposite of [CompetencyModule] in every way that matters:
 *
 * * **Scoped to a task, not a competency.** It answers "how do I do *this*".
 * * **Disposable.** No version, no status, no approval — nobody stands between a hire and their
 *   orientation. Regenerating is cheaper than maintaining, so this table is a *cache*, and losing a
 *   row costs one AI call rather than a PM's work.
 * * **Cached against the corpus it was built from.** [corpusFingerprint] is sent back to the AI
 *   service on every read; an unchanged corpus is answered without retrieval, and a corpus that has
 *   moved is re-assembled rather than served from here. A packet that describes code which has since
 *   changed is worse than no packet, because a hire cannot tell.
 *
 * One packet per `(taskProposalId, projectId)`, not per hire: orientation is a property of the task,
 * so two people who claim it read the same thing and can talk about it.
 *
 * [origin] decides which of the above applies. Everything in this doc describes an [OrientationOrigin.AI]
 * packet; a [OrientationOrigin.HUMAN] packet is a person's own words, served as-is and exempt from the
 * whole staleness dance -- see [OrientationOrigin].
 */
@Entity
@Table(
    name = "task_orientation_packets",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_task_orientation_packets_task_project",
            columnNames = ["task_proposal_id", "project_id"],
        ),
    ],
)
class TaskOrientationPacket(
    @Id
    val id: UUID = UUID.randomUUID(),
    /** The [StarterWorkTaskProposal] this packet orients somebody for. */
    @Column(name = "task_proposal_id", nullable = false)
    val taskProposalId: UUID,
    /**
     * The project whose corpus this packet was assembled from. Not nullable: material that claims
     * no project is material grounded in nothing.
     */
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    @Column(name = "task_title", nullable = false)
    var taskTitle: String,
    /**
     * Who authored this packet. AI-assembled packets are cached and revalidated against the corpus;
     * a HUMAN packet is served as-is and never re-assembled or auto-deleted (see [OrientationOrigin]).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var origin: OrientationOrigin = OrientationOrigin.AI,
    @Column(nullable = true, columnDefinition = "TEXT")
    var summary: String? = null,
    /** The corpus fingerprint this packet was assembled from; drives staleness. */
    @Column(name = "corpus_fingerprint", nullable = true)
    var corpusFingerprint: String? = null,
    @Column(nullable = true)
    var model: String? = null,
    @Column(name = "assembled_at", nullable = false)
    var assembledAt: Instant = Instant.now(),
    @OneToMany(mappedBy = "packet", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position ASC")
    val sections: MutableList<TaskOrientationSection> = mutableListOf(),
    @OneToMany(mappedBy = "packet", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position ASC")
    val sources: MutableList<TaskOrientationSource> = mutableListOf(),
)
