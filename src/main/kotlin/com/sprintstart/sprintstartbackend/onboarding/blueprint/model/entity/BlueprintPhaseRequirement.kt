package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.RequirementType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "blueprint_phase_requirements",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_phase_requirement_reference",
            columnNames = ["blueprint_phase_id", "type", "reference_id"],
        ),
    ],
)
class BlueprintPhaseRequirement(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blueprint_phase_id", nullable = false)
    val blueprintPhase: BlueprintPhase,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: RequirementType, // ROLE or SKILL
    @Column(nullable = false)
    val referenceId: UUID, // link to actual skill
    @Column(nullable = false)
    val displayName: String, // immutable snapshot for version history
)
