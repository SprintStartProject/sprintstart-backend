package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.util.UUID

@Entity
@Table(name = "blueprint_paths")
class BlueprintPath(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    val blueprintKey: UUID, // This should be created once on the initial create and then reused on edits
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    var title: String,
    var description: String? = null,
    @Column(nullable = false)
    val version: Int = 0,
    @Column(nullable = false)
    @Version
    var revision: Long = 0,
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: BlueprintStatus = BlueprintStatus.DRAFT,
    @Column(nullable = false)
    @OneToMany(
        mappedBy = "blueprintPath",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    val blueprintPhases: MutableList<BlueprintPhase> = mutableListOf(),
)
