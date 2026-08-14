package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.KnowledgeRequestStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CanonicalAnswer
import com.sprintstart.sprintstartbackend.onboarding.model.entity.KnowledgeRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.CanonicalAnswerResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.KnowledgeRequestResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.toResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.CanonicalAnswerRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.KnowledgeRequestRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * The buddy's growth loop: escalate a gap to a person, keep the human's answer as durable
 * knowledge, and serve it back to the next hire who asks something like it.
 *
 * The [CanonicalAnswer] store is checked directly (see [searchForUser]) rather than embedded into
 * the retrieval corpus, so a PM's answer is usable the instant they save it and is served verbatim
 * rather than chunked — the RAG-ingestion half of the "both" decision is a later addition.
 */
@Service
class KnowledgeBaseService(
    private val knowledgeRequestRepository: KnowledgeRequestRepository,
    private val canonicalAnswerRepository: CanonicalAnswerRepository,
    private val userApi: UserApi,
) {
    /**
     * Records a hire's escalation of a question the buddy could not answer.
     *
     * @throws ResponseStatusException 400 for a blank question, 404 when the hire is not a member of
     *   the project (a question has no owner outside a project they belong to).
     */
    @Transactional
    fun escalate(authId: String, projectId: UUID, question: String): KnowledgeRequestResponse {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A question is required.")
        }
        val hireId = resolveUserId(authId)
        if (!userApi.userHasAccessToProject(authId, projectId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "You are not a member of that project.")
        }
        val saved = knowledgeRequestRepository.save(
            KnowledgeRequest(projectId = projectId, hireId = hireId, question = trimmed),
        )
        return saved.toResponse(answer = null)
    }

    /** The PM inbox: the open queue for a project, longest-waiting first. */
    @Transactional(readOnly = true)
    fun listOpen(projectId: UUID): List<KnowledgeRequestResponse> =
        knowledgeRequestRepository
            .findAllByProjectIdAndStatusOrderByCreatedAtAsc(projectId, KnowledgeRequestStatus.OPEN)
            .map { it.toResponse(answer = null) }

    /** A hire's own escalations, newest first, each carrying its answer once one exists. */
    @Transactional(readOnly = true)
    fun listMine(authId: String): List<KnowledgeRequestResponse> {
        val hireId = resolveUserId(authId)
        val requests = knowledgeRequestRepository.findAllByHireIdOrderByCreatedAtDesc(hireId)
        val answersById = canonicalAnswerRepository
            .findAllById(requests.mapNotNull { it.canonicalAnswerId })
            .associateBy { it.id }
        return requests.map { request ->
            request.toResponse(answer = request.canonicalAnswerId?.let { answersById[it] })
        }
    }

    /**
     * A PM answers an open request: mints the durable answer and closes the request against it.
     *
     * @throws ResponseStatusException 400 for a blank answer, 404 when the request does not exist.
     */
    @Transactional
    fun answer(
        pmAuthId: String,
        requestId: UUID,
        answerText: String,
        questionOverride: String?,
    ): CanonicalAnswerResponse {
        val trimmedAnswer = answerText.trim()
        if (trimmedAnswer.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "An answer is required.")
        }
        val authorId = resolveUserId(pmAuthId)
        val request = knowledgeRequestRepository.findById(requestId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No such knowledge request: $requestId")
        }

        val canonical = canonicalAnswerRepository.save(
            CanonicalAnswer(
                projectId = request.projectId,
                question = questionOverride?.trim()?.ifEmpty { null } ?: request.question,
                answer = trimmedAnswer,
                authorId = authorId,
            ),
        )

        request.status = KnowledgeRequestStatus.ANSWERED
        request.answeredBy = authorId
        request.answeredAt = Instant.now()
        request.canonicalAnswerId = canonical.id
        knowledgeRequestRepository.save(request)

        return canonical.toResponse()
    }

    /** A PM edits a durable answer when reality changes; re-stamps authorship and the update time. */
    @Transactional
    fun editAnswer(
        pmAuthId: String,
        answerId: UUID,
        question: String,
        answer: String,
    ): CanonicalAnswerResponse {
        if (question.isBlank() || answer.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Question and answer are required.")
        }
        val canonical = canonicalAnswerRepository.findById(answerId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No such canonical answer: $answerId")
        }
        canonical.question = question.trim()
        canonical.answer = answer.trim()
        canonical.authorId = resolveUserId(pmAuthId)
        canonical.updatedAt = Instant.now()
        return canonicalAnswerRepository.save(canonical).toResponse()
    }

    /** Closes a request a PM decided needs no durable answer (a one-off or a duplicate). */
    @Transactional
    fun dismiss(requestId: UUID) {
        val request = knowledgeRequestRepository.findById(requestId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No such knowledge request: $requestId")
        }
        request.status = KnowledgeRequestStatus.DISMISSED
        knowledgeRequestRepository.save(request)
    }

    /** Every canonical answer on a project, for a PM to manage. */
    @Transactional(readOnly = true)
    fun listAnswers(projectId: UUID): List<CanonicalAnswerResponse> =
        canonicalAnswerRepository.findAllByProjectIdOrderByUpdatedAtDesc(projectId).map { it.toResponse() }

    /**
     * The buddy's canonical-answer lookup: durable human answers on the caller's project(s) that
     * match [query], best match first. Scored by how many query terms appear in the question or
     * answer — a deliberately simple, explainable match, since a served PM answer must be exactly
     * what the human wrote, not a paraphrase.
     */
    @Transactional(readOnly = true)
    fun searchForUser(userId: UUID, query: String): List<CanonicalAnswer> {
        val projectIds = userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            ?.map { it.projectId }
            .orEmpty()
        if (projectIds.isEmpty()) return emptyList()

        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()

        return canonicalAnswerRepository
            .findAllByProjectIdIn(projectIds)
            .map { it to score(it, tokens) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_SEARCH_RESULTS)
            .map { it.first }
    }

    private fun tokenize(text: String): Set<String> =
        text
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= MIN_TOKEN_LENGTH }
            .toSet()

    private fun score(answer: CanonicalAnswer, tokens: Set<String>): Int {
        val haystack = "${answer.question} ${answer.answer}".lowercase()
        return tokens.count { haystack.contains(it) }
    }

    private fun resolveUserId(authId: String): UUID =
        userApi.getUserIdByAuthId(authId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId")
        }

    private companion object {
        const val MAX_SEARCH_RESULTS = 3
        const val MIN_TOKEN_LENGTH = 3
    }
}
