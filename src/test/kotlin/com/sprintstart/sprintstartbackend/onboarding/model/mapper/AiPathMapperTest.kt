package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiCheckOption
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.external.model.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.external.model.PathPhase
import com.sprintstart.sprintstartbackend.onboarding.external.model.PhaseCheck
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiPathMapperTest {
    private val userId = UUID.randomUUID()

    private fun pathWith(check: PhaseCheck) =
        OnboardingPath(
            workingArea = "backend",
            phases = listOf(PathPhase(title = "Setup", position = 1, check = check)),
        )

    @Test
    fun `maps generated multiple-choice and short-text questions onto the phase`() {
        val check = PhaseCheck(
            questions = listOf(
                AiCheckQuestion(
                    position = 0,
                    type = "MULTIPLE_CHOICE",
                    question = "Which command?",
                    explanation = "Because.",
                    options = listOf(
                        AiCheckOption(position = 0, label = "gradlew bootRun", correct = true),
                        AiCheckOption(position = 1, label = "npm start", correct = false),
                    ),
                ),
                AiCheckQuestion(
                    position = 1,
                    type = "SHORT_TEXT",
                    question = "Start command?",
                    correctAnswer = "gradlew bootRun",
                ),
            ),
        )

        val phase = pathWith(check).toEntities(userId).phases.single()

        assertEquals(2, phase.checkQuestions.size)
        val mc = phase.checkQuestions.first { it.type == CheckQuestionType.MULTIPLE_CHOICE }
        assertEquals(2, mc.options.size)
        assertTrue(mc.options.any { it.correct })
        val text = phase.checkQuestions.first { it.type == CheckQuestionType.SHORT_TEXT }
        assertEquals("gradlew bootRun", text.correctAnswer)
        assertTrue(text.options.isEmpty())
    }

    @Test
    fun `skips invalid generated questions instead of failing path assembly`() {
        val check = PhaseCheck(
            questions = listOf(
                // MC with only one option -> invalid
                AiCheckQuestion(
                    position = 0,
                    type = "MULTIPLE_CHOICE",
                    question = "too few options",
                    options = listOf(AiCheckOption(position = 0, label = "only", correct = true)),
                ),
                // MC with no correct option -> invalid
                AiCheckQuestion(
                    position = 1,
                    type = "MULTIPLE_CHOICE",
                    question = "no correct",
                    options = listOf(
                        AiCheckOption(position = 0, label = "a", correct = false),
                        AiCheckOption(position = 1, label = "b", correct = false),
                    ),
                ),
                // SHORT_TEXT without reference answer -> invalid
                AiCheckQuestion(position = 2, type = "SHORT_TEXT", question = "no answer"),
                // Unknown type -> invalid
                AiCheckQuestion(position = 3, type = "ESSAY", question = "unsupported"),
                // Blank question -> invalid
                AiCheckQuestion(position = 4, type = "SHORT_TEXT", question = "  ", correctAnswer = "x"),
                // One valid question remains
                AiCheckQuestion(position = 5, type = "SHORT_TEXT", question = "ok?", correctAnswer = "yes"),
            ),
        )

        val phase = pathWith(check).toEntities(userId).phases.single()

        assertEquals(1, phase.checkQuestions.size)
        assertEquals("ok?", phase.checkQuestions.single().question)
    }

    @Test
    fun `a phase without a generated check has no questions`() {
        val phase = pathWith(PhaseCheck()).toEntities(userId).phases.single()

        assertTrue(phase.checkQuestions.isEmpty())
    }
}
