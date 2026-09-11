package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckOption
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.model.entity.QuestionAttempt
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetOnboardingQuestionForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetPhaseQuestionsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionAttemptResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionForAdminResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionOptionForAdminResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionOptionForUserResponse

//  ========================== Response mappers ==========================

fun PhaseCheckQuestion.toForUserResponse(status: QuestionStatus): GetOnboardingQuestionForUserResponse {
    return GetOnboardingQuestionForUserResponse(
        id = this.id,
        phaseId = this.phase.id,
        position = this.position,
        type = this.type,
        question = this.question,
        options = options.sortedBy { it.position }.map { it.toForUserResponse() },
        status = status,
        title = this.title,
        graphX = this.graphX,
        graphY = this.graphY,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}

fun PhaseCheckOption.toForUserResponse(): QuestionOptionForUserResponse {
    return QuestionOptionForUserResponse(
        id = this.id,
        position = this.position,
        label = this.label,
    )
}

fun PhaseCheckQuestion.toForAdminResponse(): QuestionForAdminResponse {
    return QuestionForAdminResponse(
        id = this.id,
        position = this.position,
        type = this.type,
        question = this.question,
        explanation = this.explanation,
        correctAnswer = this.correctAnswer,
        options = options.sortedBy { it.position }.map { it.toForAdminResponse() },
        title = this.title,
        graphX = this.graphX,
        graphY = this.graphY,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}

fun PhaseCheckOption.toForAdminResponse(): QuestionOptionForAdminResponse {
    return QuestionOptionForAdminResponse(
        id = this.id,
        position = this.position,
        label = this.label,
        correct = this.correct,
    )
}

fun OnboardingPhase.toPhaseQuestionsResponse(): GetPhaseQuestionsResponse {
    return GetPhaseQuestionsResponse(
        phaseId = this.id,
        questions = this.checkQuestions.sortedBy { it.position }.map { it.toForAdminResponse() },
    )
}

fun QuestionAttempt.toResponse(): QuestionAttemptResponse {
    return QuestionAttemptResponse(
        id = this.id,
        correct = this.correct,
        createdAt = this.createdAt,
        selectedOptionIds = this.selectedOptionIds.toList(),
        textAnswer = this.textAnswer,
    )
}
