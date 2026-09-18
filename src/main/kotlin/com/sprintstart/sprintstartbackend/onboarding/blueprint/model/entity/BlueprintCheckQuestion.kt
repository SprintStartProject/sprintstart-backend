package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table

@Entity
@Table(name = "blueprint_check_questions")
class BlueprintCheckQuestion(
    blueprintPhase: BlueprintPhase,
    title: String,
    graphX: Double? = null,
    graphY: Double? = null,
    @Column(nullable = false)
    var position: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: CheckQuestionType,
    @Column(nullable = false, columnDefinition = "TEXT")
    var question: String,
    @Column(nullable = true, columnDefinition = "TEXT")
    var explanation: String? = null,
    // Expected answer for SHORT_TEXT questions, null for MULTIPLE_CHOICE
    @Column(nullable = true, columnDefinition = "TEXT")
    var correctAnswer: String? = null,
    @OneToMany(
        mappedBy = "blueprintCheckQuestion",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @OrderBy("position ASC")
    val blueprintCheckOptions: MutableList<BlueprintCheckOption> = mutableListOf(),
) : BlueprintSubGraphNode(
        blueprintPhase = blueprintPhase,
        title = title,
        graphX = graphX,
        graphY = graphY,
    )
