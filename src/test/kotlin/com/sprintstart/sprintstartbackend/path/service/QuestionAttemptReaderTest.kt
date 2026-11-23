package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.path.model.entity.QuestionAttempt
import com.sprintstart.sprintstartbackend.path.repository.QuestionAttemptRepository
import com.sprintstart.sprintstartbackend.shared.ids.CheckQuestionId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuestionAttemptReaderTest {
    private val questionAttemptRepository: QuestionAttemptRepository = mockk()
    private val reader = QuestionAttemptReader(questionAttemptRepository)

    private val questionId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @Test
    fun `the newest attempt is the last one`() {
        val option = UUID.randomUUID()
        val newest = attempt(correct = false, text = "The PM", options = listOf(option))
        every { questionAttemptRepository.findAllByQuestionIdAndUserIdOrderByCreatedAtDesc(questionId, userId) } returns
            mutableListOf(newest, attempt(correct = true, text = "The team"))

        val last = reader.lastAttempt(CheckQuestionId(questionId), UserId(userId))

        assertEquals(newest.id, last?.id)
        assertEquals(false, last?.correct)
        assertEquals("The PM", last?.textAnswer)
        assertEquals(listOf(option), last?.selectedOptionIds)
    }

    @Test
    fun `a question never answered has no last attempt`() {
        every { questionAttemptRepository.findAllByQuestionIdAndUserIdOrderByCreatedAtDesc(questionId, userId) } returns
            mutableListOf()

        assertNull(reader.lastAttempt(CheckQuestionId(questionId), UserId(userId)))
    }

    private fun attempt(correct: Boolean, text: String, options: List<UUID> = emptyList()) = QuestionAttempt(
        questionId = questionId,
        userId = userId,
        correct = correct,
        selectedOptionIds = options.toMutableList(),
        textAnswer = text,
    )
}
