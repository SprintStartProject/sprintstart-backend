package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationStep
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.util.UUID

/**
 * One ordered section of a [TaskOrientationPacket], belonging to exactly one step.
 *
 * Its [citations] are stored as real rows rather than folded into the body, because provenance is
 * the trust mechanism here: a hire has to be able to open the source and check the claim. A section
 * with no citations should not exist — the AI service drops ungrounded ones before they are ever
 * returned — and [TaskOrientationCitation] is what makes that guarantee survive persistence.
 */
@Entity
@Table(name = "task_orientation_sections")
class TaskOrientationSection(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "packet_id", nullable = false)
    val packet: TaskOrientationPacket,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var step: OrientationStep,
    @Column(nullable = false)
    var title: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    var body: String,
    @Column(nullable = false)
    var position: Int,
    @OneToMany(mappedBy = "section", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("position ASC")
    val citations: MutableList<TaskOrientationCitation> = mutableListOf(),
)
