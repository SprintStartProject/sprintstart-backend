package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.SubmitQuestionAttemptRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.UpdateOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.UpdatePhaseQuestionsRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.UpdateQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetPhaseQuestionsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetQuestionAttemptsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionAttemptResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionForAdminResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionOptionForAdminResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.SubmitQuestionAttemptResponse
import com.sprintstart.sprintstartbackend.onboarding.service.QuestionAttemptService
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@WebMvcTest(QuestionController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class QuestionControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var questionAttemptService: QuestionAttemptService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val phaseId = UUID.randomUUID()
    private val questionId = UUID.randomUUID()
    private val attemptId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val authId = "test-auth-id"
    private val adminAuthId = "test-admin-auth-id"
    private val timestamp = Instant.parse("2026-06-23T09:00:00Z")

    private fun jwtWithSubject(
        subject: String,
        vararg roles: String,
    ): JwtRequestPostProcessor {
        return jwt()
            .jwt { jwt ->
                jwt.subject(subject)
                jwt.claim(
                    "realm_access",
                    mapOf("roles" to roles.toList()),
                )
            }.authorities(
                roles.map { role -> SimpleGrantedAuthority("ROLE_$role") },
            )
    }

    private val userJwt = jwtWithSubject(authId, "USER")
    private val adminJwt = jwtWithSubject(adminAuthId, "USER", "ADMIN")
    private val noUserRoleJwt = jwtWithSubject(authId, "NONE")

    private fun buildSubmitRequest() = SubmitQuestionAttemptRequest(
        selectedOptionIds = listOf(UUID.randomUUID()),
    )

    private fun buildSubmitResponse() = SubmitQuestionAttemptResponse(
        attemptId = attemptId,
        questionId = questionId,
        correct = true,
        createdAt = timestamp,
        explanation = "Because correct answers unblock the journey",
        status = QuestionStatus.PASSED,
        onboardingCompleted = false,
    )

    private fun buildPhaseQuestionsResponse() = GetPhaseQuestionsResponse(
        phaseId = phaseId,
        questions = listOf(
            QuestionForAdminResponse(
                id = questionId,
                position = 1,
                type = CheckQuestionType.MULTIPLE_CHOICE,
                question = "What unblocks a waiting step?",
                explanation = "A passed question unblocks what it blocks",
                options = listOf(
                    QuestionOptionForAdminResponse(
                        id = UUID.randomUUID(),
                        position = 1,
                        label = "A correct answer",
                        correct = true,
                    ),
                    QuestionOptionForAdminResponse(
                        id = UUID.randomUUID(),
                        position = 2,
                        label = "A wrong answer",
                        correct = false,
                    ),
                ),
            ),
        ),
    )

    private fun buildReplaceRequest() = UpdatePhaseQuestionsRequest(
        questions = listOf(
            UpdateQuestionRequest(
                position = 1,
                type = CheckQuestionType.MULTIPLE_CHOICE,
                question = "What unblocks a waiting step?",
                options = listOf(
                    UpdateOptionRequest(position = 1, label = "A correct answer", correct = true),
                    UpdateOptionRequest(position = 2, label = "A wrong answer", correct = false),
                ),
            ),
        ),
    )

    private fun buildAttemptsResponse() = GetQuestionAttemptsResponse(
        userId = userId,
        questionId = questionId,
        attempts = listOf(
            QuestionAttemptResponse(
                id = attemptId,
                correct = false,
                createdAt = timestamp,
                selectedOptionIds = emptyList(),
                textAnswer = "I do not know",
            ),
        ),
    )

    // ========================== /me endpoints ==========================

    @Test
    fun `submitQuestionAttemptForMe should return 201 and the graded attempt`() {
        val request = buildSubmitRequest()
        every {
            questionAttemptService.submitQuestionAttemptForMe(authId, questionId, request)
        } returns buildSubmitResponse()

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/questions/$questionId/attempts")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.attemptId").value(attemptId.toString()))
            .andExpect(jsonPath("$.questionId").value(questionId.toString()))
            .andExpect(jsonPath("$.correct").value(true))
            .andExpect(jsonPath("$.status").value(QuestionStatus.PASSED.name))

        verify(exactly = 1) {
            questionAttemptService.submitQuestionAttemptForMe(authId, questionId, request)
        }
    }

    @Test
    fun `submitQuestionAttemptForMe should return 401 when not authenticated`() {
        val request = buildSubmitRequest()

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/questions/$questionId/attempts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `submitQuestionAttemptForMe should return 403 when authenticated with wrong role`() {
        val request = buildSubmitRequest()

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/questions/$questionId/attempts")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `submitQuestionAttemptForMe should return 404 when the question is not on the user's path`() {
        val request = buildSubmitRequest()
        every {
            questionAttemptService.submitQuestionAttemptForMe(authId, questionId, request)
        } throws ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/questions/$questionId/attempts")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) {
            questionAttemptService.submitQuestionAttemptForMe(authId, questionId, request)
        }
    }

    // ========================== Admin endpoints ==========================

    @Test
    fun `getPhaseQuestions should return 200 and the questions with correct answers`() {
        every { questionAttemptService.getPhaseQuestions(phaseId) } returns buildPhaseQuestionsResponse()

        mockMvc
            .perform(get("/api/v1/onboarding/phases/$phaseId/questions").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.phaseId").value(phaseId.toString()))
            .andExpect(jsonPath("$.questions[0].id").value(questionId.toString()))
            .andExpect(jsonPath("$.questions[0].options[0].correct").value(true))

        verify(exactly = 1) { questionAttemptService.getPhaseQuestions(phaseId) }
    }

    @Test
    fun `getPhaseQuestions should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/phases/$phaseId/questions"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getPhaseQuestions should return 403 when authenticated without an admin role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/phases/$phaseId/questions").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getPhaseQuestions should return 404 when the phase does not exist`() {
        every { questionAttemptService.getPhaseQuestions(phaseId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(get("/api/v1/onboarding/phases/$phaseId/questions").with(adminJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { questionAttemptService.getPhaseQuestions(phaseId) }
    }

    @Test
    fun `replacePhaseQuestions should return 200 and the stored questions`() {
        val request = buildReplaceRequest()
        every { questionAttemptService.replacePhaseQuestions(phaseId, request) } returns
            buildPhaseQuestionsResponse()

        mockMvc
            .perform(
                put("/api/v1/onboarding/phases/$phaseId/questions")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.phaseId").value(phaseId.toString()))

        verify(exactly = 1) { questionAttemptService.replacePhaseQuestions(phaseId, request) }
    }

    @Test
    fun `replacePhaseQuestions should return 400 when a question is invalid for its type`() {
        val request = buildReplaceRequest()
        every { questionAttemptService.replacePhaseQuestions(phaseId, request) } throws
            ResponseStatusException(HttpStatus.BAD_REQUEST)

        mockMvc
            .perform(
                put("/api/v1/onboarding/phases/$phaseId/questions")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isBadRequest)

        verify(exactly = 1) { questionAttemptService.replacePhaseQuestions(phaseId, request) }
    }

    @Test
    fun `replacePhaseQuestions should return 401 when not authenticated`() {
        val request = buildReplaceRequest()

        mockMvc
            .perform(
                put("/api/v1/onboarding/phases/$phaseId/questions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `replacePhaseQuestions should return 403 when authenticated without an admin role`() {
        val request = buildReplaceRequest()

        mockMvc
            .perform(
                put("/api/v1/onboarding/phases/$phaseId/questions")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `replacePhaseQuestions should return 404 when the phase does not exist`() {
        val request = buildReplaceRequest()
        every { questionAttemptService.replacePhaseQuestions(phaseId, request) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                put("/api/v1/onboarding/phases/$phaseId/questions")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) { questionAttemptService.replacePhaseQuestions(phaseId, request) }
    }

    @Test
    fun `getQuestionAttemptsForUser should return 200 and the user's attempts`() {
        every { questionAttemptService.getQuestionAttemptsForUser(userId, questionId) } returns
            buildAttemptsResponse()

        mockMvc
            .perform(
                get("/api/v1/onboarding/users/$userId/questions/$questionId/attempts")
                    .with(adminJwt),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.questionId").value(questionId.toString()))
            .andExpect(jsonPath("$.attempts[0].correct").value(false))

        verify(exactly = 1) { questionAttemptService.getQuestionAttemptsForUser(userId, questionId) }
    }

    @Test
    fun `getQuestionAttemptsForUser should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/users/$userId/questions/$questionId/attempts"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getQuestionAttemptsForUser should return 403 when authenticated without an admin role`() {
        mockMvc
            .perform(
                get("/api/v1/onboarding/users/$userId/questions/$questionId/attempts")
                    .with(userJwt),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `getQuestionAttemptsForUser should return 404 when the user or question does not exist`() {
        every { questionAttemptService.getQuestionAttemptsForUser(userId, questionId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                get("/api/v1/onboarding/users/$userId/questions/$questionId/attempts")
                    .with(adminJwt),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) { questionAttemptService.getQuestionAttemptsForUser(userId, questionId) }
    }
}
