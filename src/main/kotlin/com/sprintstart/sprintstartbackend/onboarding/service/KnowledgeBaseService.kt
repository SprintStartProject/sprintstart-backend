package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.KnowledgeRequestStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CanonicalAnswer
import com.sprintstart.sprintstartbackend.onboarding.model.entity.KnowledgeRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.CanonicalAnswerResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.EscalationHireResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.KnowledgeRequestResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.toResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.CanonicalAnswerRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.KnowledgeRequestRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
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
@Suppress("TooManyFunctions") // The inbox, the canonical answers, and the team-mode buddy's guarded writes to both.
class KnowledgeBaseService(
    private val knowledgeRequestRepository: KnowledgeRequestRepository,
    private val canonicalAnswerRepository: CanonicalAnswerRepository,
    private val userApi: UserApi,
    private val onboardingPositionReader: OnboardingPositionReader,
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

    /**
     * The PM inbox: the open queue for a project, longest-waiting first, each question carrying who
     * asked it and where they are.
     *
     * The identity was always stored and always serialised, but a UUID is not something a PM can act
     * on: to answer well they need to know whether they are talking to somebody on their first day or
     * somebody three weeks in. Resolved here rather than left to the client, which would turn one
     * inbox into a request per row.
     *
     * The users and the positions are each fetched once for the *distinct* askers, so a queue of
     * twenty questions from three people costs three people's worth of lookups rather than twenty.
     * Walking each path's phases and steps is still lazy, as the team overview's own derivation
     * is, so this grows with the number of distinct askers and their phases — which is why the
     * sidebar badge counts through [countOpen] instead of reading this and taking its length.
     */
    @Transactional(readOnly = true)
    fun listOpen(projectId: UUID): List<KnowledgeRequestResponse> {
        val requests = knowledgeRequestRepository
            .findAllByProjectIdAndStatusOrderByCreatedAtAsc(projectId, KnowledgeRequestStatus.OPEN)
        if (requests.isEmpty()) return emptyList()

        val hireIds = requests.map { it.hireId }.distinct()
        val usersById = userApi.getUsersByIds(hireIds).associateBy { it.id }
        val positionsById = onboardingPositionReader.positionsFor(hireIds)

        return requests.map { request ->
            val user = usersById[request.hireId]
            val hire = user?.let {
                val position = positionsById[it.id]
                EscalationHireResponse(
                    userId = it.id,
                    displayName = it.displayName(),
                    profileIcon = it.profileIcon,
                    currentPhase = position?.currentPhase,
                    currentStep = position?.currentStep,
                    progressPercentage = position?.progressPercentage ?: 0.0,
                )
            }
            request.toResponse(answer = null, hire = hire)
        }
    }

    /**
     * How many questions on a project are still waiting on a person.
     *
     * Deliberately not `listOpen(projectId).size`. The sidebar badge asks this on every navigation,
     * and [listOpen] resolves every asker's name and onboarding position — a page of work to
     * produce one integer. Counted in the database instead.
     */
    @Transactional(readOnly = true)
    fun countOpen(projectId: UUID): Long =
        knowledgeRequestRepository.countByProjectIdAndStatus(projectId, KnowledgeRequestStatus.OPEN)

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

    /**
     * Answers a request only while it is still open on [projectId], for the team-mode buddy.
     *
     * Unlike [answer], which the inbox calls, the open check is part of the write: the request is closed
     * with one conditional update, and the canonical answer saved in the same transaction is rolled back
     * if that update finds the request already closed. A manager confirming an answer while the same
     * question is answered or dismissed elsewhere is refused, instead of publishing a second answer or an
     * answer against a dismissed question.
     *
     * @throws ResponseStatusException 400 for a blank answer; 404 when the request is not on [projectId];
     * 409 when it is no longer open.
     */
    @Transactional
    fun answerOpenOn(
        pmAuthId: String,
        projectId: UUID,
        requestId: UUID,
        answerText: String,
        questionOverride: String?,
    ): CanonicalAnswerResponse {
        val trimmedAnswer = answerText.trim().ifEmpty {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "An answer is required.")
        }
        val authorId = resolveUserId(pmAuthId)
        val request = openRequestOn(projectId, requestId)

        // Flushed now: the conditional update below clears the persistence context, which would
        // otherwise discard an insert that had not been written yet.
        val canonical = canonicalAnswerRepository.saveAndFlush(
            CanonicalAnswer(
                projectId = projectId,
                question = questionOverride?.trim()?.ifEmpty { null } ?: request.question,
                answer = trimmedAnswer,
                authorId = authorId,
            ),
        )
        val closed = knowledgeRequestRepository.answerIfOpen(
            id = requestId,
            projectId = projectId,
            open = KnowledgeRequestStatus.OPEN,
            answered = KnowledgeRequestStatus.ANSWERED,
            answeredBy = authorId,
            answeredAt = Instant.now(),
            canonicalAnswerId = canonical.id,
        )
        if (closed == 0) {
            // Rolls the canonical answer back with it: the question closed between the check and this write.
            throw ResponseStatusException(HttpStatus.CONFLICT, NO_LONGER_OPEN)
        }
        return canonical.toResponse()
    }

    /**
     * Dismisses a request only while it is still open on [projectId], for the team-mode buddy.
     *
     * @throws ResponseStatusException 409 when it is no longer open there.
     */
    @Transactional
    fun dismissOpenOn(projectId: UUID, requestId: UUID) {
        val dismissed = knowledgeRequestRepository.dismissIfOpen(
            id = requestId,
            projectId = projectId,
            open = KnowledgeRequestStatus.OPEN,
            dismissed = KnowledgeRequestStatus.DISMISSED,
        )
        if (dismissed == 0) {
            throw ResponseStatusException(HttpStatus.CONFLICT, NO_LONGER_OPEN)
        }
    }

    /**
     * Rewords an answer on [projectId] only while it is unchanged since [seenUpdatedAt], for the
     * team-mode buddy, so an edit confirmed from a preview never overwrites wording written after it.
     *
     * @throws ResponseStatusException 400 for a blank question or answer; 409 when the answer changed since
     * it was read, or is not on [projectId].
     */
    @Transactional
    @Suppress("LongParameterList") // The edit, the caller, and the version of the answer it was made against.
    fun editAnswerIfUnchanged(
        pmAuthId: String,
        projectId: UUID,
        answerId: UUID,
        question: String,
        answer: String,
        seenUpdatedAt: Instant,
    ): CanonicalAnswerResponse {
        if (question.isBlank() || answer.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Question and answer are required.")
        }
        val edited = canonicalAnswerRepository.editIfUnchanged(
            id = answerId,
            projectId = projectId,
            question = question.trim(),
            answer = answer.trim(),
            authorId = resolveUserId(pmAuthId),
            updatedAt = Instant.now(),
            seenUpdatedAt = seenUpdatedAt,
        )
        if (edited == 0) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "That answer was changed after it was shown, so it was not overwritten.",
            )
        }
        return canonicalAnswerRepository
            .findById(answerId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No such canonical answer: $answerId") }
            .toResponse()
    }

    /**
     * The request [requestId] names on [projectId], if it is still open.
     *
     * @throws ResponseStatusException 404 when it is not on that project; 409 when it is no longer open.
     */
    private fun openRequestOn(projectId: UUID, requestId: UUID): KnowledgeRequest {
        val request = knowledgeRequestRepository
            .findById(requestId)
            .orElse(null)
            ?.takeIf { it.projectId == projectId }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such knowledge request on this project.")
        if (request.status != KnowledgeRequestStatus.OPEN) {
            throw ResponseStatusException(HttpStatus.CONFLICT, NO_LONGER_OPEN)
        }
        return request
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

    private fun resolveUserId(authId: String): UUID =
        userApi.getUserIdByAuthId(authId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId")
        }

    private companion object {
        const val MAX_SEARCH_RESULTS = 3
        const val NO_LONGER_OPEN = "That question is no longer open: it was answered or dismissed in the meantime."
    }
}

/** Words short enough to match anything are noise in a term-overlap score. */
private const val MIN_TOKEN_LENGTH = 3

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

/**
 * What to call somebody on screen.
 *
 * Falls back to the username rather than rendering an empty string: a blank name on a card reads as a
 * bug in the card, and the username is at least something a PM can search for.
 *
 * A file-level helper rather than a method: it is a way of phrasing a [UserDto], not a thing the
 * knowledge base does.
 */
private fun UserDto.displayName(): String = "$firstname $lastname".trim().ifEmpty { username }
