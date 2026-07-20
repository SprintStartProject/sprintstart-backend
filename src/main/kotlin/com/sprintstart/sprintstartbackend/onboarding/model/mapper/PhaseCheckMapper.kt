@file:Suppress("TooManyFunctions")

package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckAttempt
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckOption
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.CheckAttemptAnswerResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.CheckAttemptResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.CheckOptionForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.CheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.CheckQuestionForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.CheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.GetPhaseCheckForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.GetPhaseCheckResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.PhaseCheckSummaryResponse

//  ========================== Phase check state helpers ==========================

/**
 * Whether all steps of this phase are completed from the user's perspective
 * (finished or skipped). Phases without steps count as complete.
 */
fun OnboardingPhase.stepsCompleted(): Boolean {
    return steps.all { it.status == StepStatus.FINISHED || it.status == StepStatus.SKIPPED }
}

/** Whether this phase has a knowledge check the user must pass. */
fun OnboardingPhase.hasCheck(): Boolean = checkQuestions.isNotEmpty()

/** Whether any submitted attempt passed this phase's knowledge check. */
fun OnboardingPhase.checkPassed(): Boolean = checkAttempts.any { it.passed }

/** The most recently submitted attempt for this phase's knowledge check. */
fun OnboardingPhase.latestCheckAttempt(): PhaseCheckAttempt? = checkAttempts.maxByOrNull { it.createdAt }

//  ========================== Response mappers ==========================

fun OnboardingPhase.toCheckSummaryResponse(): PhaseCheckSummaryResponse {
    val latestAttempt = latestCheckAttempt()
    return PhaseCheckSummaryResponse(
        required = hasCheck(),
        questionCount = checkQuestions.size,
        passed = checkPassed(),
        latestAttemptId = latestAttempt?.id,
        latestAttemptAt = latestAttempt?.createdAt,
    )
}

fun OnboardingPhase.toCheckForUserResponse(): GetPhaseCheckForUserResponse {
    return GetPhaseCheckForUserResponse(
        phaseId = this.id,
        required = hasCheck(),
        passed = checkPassed(),
        latestAttemptId = latestCheckAttempt()?.id,
        questions = checkQuestions
            .sortedBy { it.position }
            .map { it.toForUserResponse() },
    )
}

fun PhaseCheckQuestion.toForUserResponse(): CheckQuestionForUserResponse {
    return CheckQuestionForUserResponse(
        id = this.id,
        position = this.position,
        type = this.type,
        question = this.question,
        options = options.sortedBy { it.position }.map { it.toForUserResponse() },
    )
}

fun PhaseCheckOption.toForUserResponse(): CheckOptionForUserResponse {
    return CheckOptionForUserResponse(
        id = this.id,
        position = this.position,
        label = this.label,
    )
}

fun OnboardingPhase.toCheckResponse(): GetPhaseCheckResponse {
    return GetPhaseCheckResponse(
        phaseId = this.id,
        questions = checkQuestions
            .sortedBy { it.position }
            .map { it.toGetResponse() },
    )
}

fun PhaseCheckQuestion.toGetResponse(): CheckQuestionResponse {
    return CheckQuestionResponse(
        id = this.id,
        position = this.position,
        type = this.type,
        question = this.question,
        explanation = this.explanation,
        correctAnswer = this.correctAnswer,
        options = options.sortedBy { it.position }.map { it.toGetResponse() },
    )
}

fun PhaseCheckOption.toGetResponse(): CheckOptionResponse {
    return CheckOptionResponse(
        id = this.id,
        position = this.position,
        label = this.label,
        correct = this.correct,
    )
}

fun PhaseCheckAttempt.toGetResponse(questionCount: Int): CheckAttemptResponse {
    return CheckAttemptResponse(
        id = this.id,
        passed = this.passed,
        createdAt = this.createdAt,
        correctAnswerCount = answers.count { it.correct },
        questionCount = questionCount,
        answers = answers.map { answer ->
            CheckAttemptAnswerResponse(
                questionId = answer.questionId,
                selectedOptionIds = answer.selectedOptionIds.toList(),
                textAnswer = answer.textAnswer,
                correct = answer.correct,
            )
        },
    )
}
