package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.insights.external.InsightsRefreshApi
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.KnowledgeRequestStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CanonicalAnswer
import com.sprintstart.sprintstartbackend.onboarding.model.entity.KnowledgeRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.CanonicalAnswerRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.KnowledgeRequestRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/*
 * The actions of team mode's knowledge area.
 *
 * `KnowledgeBaseService.answer` and `dismiss` load a request by id and never check its project — the
 * REST routes in front of them only check the PM role. So every action here resolves its target
 * against the turn's project itself, when drafting and again when the manager confirms.
 */

/** The request [requestId] names, if it is still open and on [projectId]. */
private fun KnowledgeRequestRepository.openOn(requestId: UUID?, projectId: UUID): KnowledgeRequest? =
    requestId
        ?.let { findById(it).orElse(null) }
        ?.takeIf { it.projectId == projectId && it.status == KnowledgeRequestStatus.OPEN }

/** The canonical answer [answerId] names, if it is on [projectId]. */
private fun CanonicalAnswerRepository.on(answerId: UUID?, projectId: UUID): CanonicalAnswer? =
    answerId
        ?.let { findById(it).orElse(null) }
        ?.takeIf { it.projectId == projectId }

private const val LABEL_CHARS = 60

private fun String.forLabel(): String = if (length <= LABEL_CHARS) this else take(LABEL_CHARS - 1).trimEnd() + "…"

private const val NOT_OPEN_HERE =
    "That question is not open on this project. Call list_open_escalations for the ones that are, and pass " +
        "the request_id it gives."

private const val GONE_SINCE = "That question was answered or dismissed since, so nothing was changed."

/** A JSON schema of string fields, for an action's tool definition. */
private fun stringFields(vararg fields: Pair<String, String>, required: List<String>): JsonObject =
    buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            fields.forEach { (name, description) ->
                putJsonObject(name) {
                    put("type", "string")
                    put("description", description)
                }
            }
        }
        putJsonArray("required") { required.forEach { add(it) } }
    }

/**
 * Offers to answer a question a hire escalated, which turns the answer into a canonical answer.
 *
 * The preview carries the whole answer, because that is what the manager is agreeing to publish: once
 * confirmed, the buddy quotes it to anybody on the project who asks something similar.
 */
@Component
class AnswerEscalationAction(
    private val knowledgeRequestRepository: KnowledgeRequestRepository,
    private val knowledgeBaseService: KnowledgeBaseService,
) : TeamActionHandler {
    override val area = TeamArea.KNOWLEDGE
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "answer_escalation",
        description = "Offer to answer a question a hire escalated. Search the docs first and write the answer " +
            "from what you found; never invent process. The manager sees the full answer and publishes it by " +
            "confirming — it becomes a canonical answer the buddy quotes to the whole project. This does NOT " +
            "answer anything by itself.",
        parameters = stringFields(
            "request_id" to "The request_id from list_open_escalations.",
            "answer" to "The complete answer, written for the person who asked.",
            "question" to "Optional: the question reworded so it reads well as a lasting answer.",
            required = listOf("request_id", "answer"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val request = knowledgeRequestRepository.openOn(call.uuidArgument("request_id"), context.projectId)
            ?: return TeamActionDraft.Refused(NOT_OPEN_HERE)
        val answer = call.textArgument("answer")
        if (answer.isBlank()) {
            return TeamActionDraft.Refused("No answer was written. Draft it from the docs first, then offer it.")
        }
        val question = call.textArgument("question").ifBlank { request.question }
        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("request_id", request.id.toString())
                put("answer", answer)
                put("question", question)
            },
            label = "Answer: ${question.forLabel()}",
            preview = "Question: $question\n\nAnswer:\n$answer\n\n" +
                "This becomes a canonical answer on this project: the buddy quotes it to anyone who asks " +
                "something similar, and the person who asked sees it.",
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        GONE_SINCE.takeIf { knowledgeRequestRepository.openOn(params.uuid("request_id"), context.projectId) == null }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        knowledgeBaseService.answer(
            pmAuthId = context.authId,
            requestId = requireNotNull(params.uuid("request_id")),
            answerText = params.text("answer"),
            questionOverride = params.text("question"),
        )
        return "Answered. It is now a canonical answer on this project, and the person who asked can see it."
    }
}

/** Offers to take a question out of the inbox without answering it. */
@Component
class DismissEscalationAction(
    private val knowledgeRequestRepository: KnowledgeRequestRepository,
    private val knowledgeBaseService: KnowledgeBaseService,
) : TeamActionHandler {
    override val area = TeamArea.KNOWLEDGE
    override val risk = BuddyProposalRisk.DESTRUCTIVE
    override val spec = BuddyToolSpecDto(
        name = "dismiss_escalation",
        description = "Offer to dismiss a question a hire escalated without answering it — for a duplicate, or " +
            "one that no longer applies. Prefer answering whenever an answer exists. This does NOT dismiss " +
            "anything by itself; the manager confirms.",
        parameters = stringFields(
            "request_id" to "The request_id from list_open_escalations.",
            required = listOf("request_id"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val request = knowledgeRequestRepository.openOn(call.uuidArgument("request_id"), context.projectId)
            ?: return TeamActionDraft.Refused(NOT_OPEN_HERE)
        return TeamActionDraft.Proposed(
            params = buildJsonObject { put("request_id", request.id.toString()) },
            label = "Dismiss: ${request.question.forLabel()}",
            preview = "Dismiss the question “${request.question}” without answering it.\n\n" +
                "It leaves the inbox, the person who asked sees it as dismissed, and no canonical answer is written.",
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        GONE_SINCE.takeIf { knowledgeRequestRepository.openOn(params.uuid("request_id"), context.projectId) == null }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        knowledgeBaseService.dismiss(requireNotNull(params.uuid("request_id")))
        return "Dismissed. The question left the inbox without an answer."
    }
}

/**
 * Offers to reword a canonical answer the team already wrote.
 *
 * The preview shows before and after, and the proposal remembers when the answer was last changed: if
 * somebody edits it between the preview and the confirm, the manager would be overwriting words they
 * never saw, so the confirm refuses instead.
 */
@Component
class EditCanonicalAnswerAction(
    private val canonicalAnswerRepository: CanonicalAnswerRepository,
    private val knowledgeBaseService: KnowledgeBaseService,
) : TeamActionHandler {
    override val area = TeamArea.KNOWLEDGE
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "edit_canonical_answer",
        description = "Offer to change a canonical answer on this project: its question, its answer, or both. " +
            "Read list_canonical_answers first. The manager sees the old and new wording and confirms. This " +
            "does NOT change anything by itself.",
        parameters = stringFields(
            "answer_id" to "The answer_id from list_canonical_answers.",
            "question" to "Optional: the new question. Omit to keep the current one.",
            "answer" to "Optional: the new answer. Omit to keep the current one.",
            required = listOf("answer_id"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val current = canonicalAnswerRepository.on(call.uuidArgument("answer_id"), context.projectId)
            ?: return TeamActionDraft.Refused(
                "That answer is not on this project. Call list_canonical_answers and pass the answer_id it gives.",
            )
        val question = call.textArgument("question").ifBlank { current.question }
        val answer = call.textArgument("answer").ifBlank { current.answer }
        if (question == current.question && answer == current.answer) {
            return TeamActionDraft.Refused("Nothing would change. Give a new question, a new answer, or both.")
        }
        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("answer_id", current.id.toString())
                put("question", question)
                put("answer", answer)
                put("seen_updated_at", current.updatedAt.toString())
            },
            label = "Edit answer: ${question.forLabel()}",
            preview = "Before:\nQ: ${current.question}\nA: ${current.answer}\n\n" +
                "After:\nQ: $question\nA: $answer\n\nThe buddy quotes the new wording from now on.",
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val current = canonicalAnswerRepository.on(params.uuid("answer_id"), context.projectId)
            ?: return "That answer no longer exists, so nothing was changed."
        val seen = runCatching { Instant.parse(params.text("seen_updated_at")) }.getOrNull()
        return if (current.updatedAt == seen) {
            null
        } else {
            "Somebody changed that answer after this was proposed, so it was not overwritten. Ask again to " +
                "see the current wording."
        }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        knowledgeBaseService.editAnswer(
            pmAuthId = context.authId,
            answerId = requireNotNull(params.uuid("answer_id")),
            question = params.text("question"),
            answer = params.text("answer"),
        )
        return "Updated the canonical answer. The buddy quotes the new wording from now on."
    }
}

/** Offers to rebuild the project's recurring-question groups. */
@Component
class RefreshFaqAction(
    private val insightsRefreshApi: InsightsRefreshApi,
) : TeamActionHandler {
    override val area = TeamArea.KNOWLEDGE
    override val risk = BuddyProposalRisk.BULK
    override val spec = BuddyToolSpecDto(
        name = "refresh_faq",
        description = "Offer to rebuild this project's FAQ: every question asked here regrouped into recurring " +
            "questions. It asks the AI service and takes a while. Offer it when the manager wants the FAQ up to " +
            "date, not as a way to answer a question. Takes no arguments.",
        parameters = stringFields(required = emptyList()),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft =
        TeamActionDraft.Proposed(
            params = JsonObject(emptyMap()),
            label = "Refresh the FAQ",
            preview = "Rebuild this project's FAQ: every question asked here is regrouped into recurring " +
                "questions, replacing the current groups. It asks the AI service and can take a while.",
        )

    override fun recheck(params: JsonObject, context: TeamToolContext): String? = null

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val groups = insightsRefreshApi.refreshFaq(context.projectId)
        return "Refreshed the FAQ: $groups recurring-question groups."
    }
}

/** Offers to rescan the project's components for missing documentation. */
@Component
class RefreshKnowledgeGapsAction(
    private val insightsRefreshApi: InsightsRefreshApi,
) : TeamActionHandler {
    override val area = TeamArea.KNOWLEDGE
    override val risk = BuddyProposalRisk.BULK
    override val spec = BuddyToolSpecDto(
        name = "refresh_knowledge_gaps",
        description = "Offer to rescan this project's components for missing documentation. It asks the AI " +
            "service and takes a while. Takes no arguments.",
        parameters = stringFields(required = emptyList()),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft =
        TeamActionDraft.Proposed(
            params = JsonObject(emptyMap()),
            label = "Rescan for documentation gaps",
            preview = "Rescan this project's components for missing documentation, replacing the current list " +
                "of gaps. It asks the AI service and can take a while.",
        )

    override fun recheck(params: JsonObject, context: TeamToolContext): String? = null

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val refresh = insightsRefreshApi.refreshKnowledgeGaps(context.projectId)
        return "Rescanned ${refresh.componentCount} components: ${refresh.gapCount} are missing documentation."
    }
}
