package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintPhaseType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.test.assertEquals

internal fun blueprintPathFixture(
    projectId: UUID? = null,
    status: BlueprintStatus = BlueprintStatus.DRAFT,
): BlueprintPath {
    return BlueprintPath(
        blueprintKey = UUID.randomUUID(),
        projectId = projectId,
        title = "Blueprint",
        status = status,
    )
}

internal fun blueprintPhaseFixture(path: BlueprintPath = blueprintPathFixture()): BlueprintPhase {
    return BlueprintPhase(
        blueprintPath = path,
        position = 0,
        title = "Phase",
        description = null,
        aiPrompt = null,
        type = BlueprintPhaseType.FIXED,
    )
}

internal fun blueprintStepFixture(phase: BlueprintPhase = blueprintPhaseFixture()): BlueprintStep {
    return BlueprintStep(
        blueprintPhase = phase,
        title = "Step",
        position = 0,
        description = "Description",
        type = StepType.TASK,
        estimatedMinutes = 15,
        expectedOutcome = "Outcome",
    )
}

internal fun blueprintQuestionFixture(phase: BlueprintPhase = blueprintPhaseFixture()): BlueprintCheckQuestion {
    return BlueprintCheckQuestion(
        blueprintPhase = phase,
        title = "Question",
        position = 0,
        type = CheckQuestionType.MULTIPLE_CHOICE,
        question = "Question?",
    )
}

internal fun assertBlueprintStatus(
    status: HttpStatus,
    action: () -> Unit,
) {
    val exception = assertThrows<ResponseStatusException>(action)
    assertEquals(status, exception.statusCode)
}
