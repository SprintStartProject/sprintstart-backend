package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component
import java.time.ZoneOffset
import java.util.UUID

/**
 * The read tools of team mode's knowledge area: what the project's hires asked that nobody could
 * answer, and the answers the team has already written down.
 *
 * Both read only the turn's project. The ids they print are how the knowledge actions name their
 * targets, and every action checks again that its target belongs to that project.
 */
@Component
class KnowledgeTeamTools(
    private val knowledgeBaseService: KnowledgeBaseService,
) : TeamAreaTools {
    override val area = TeamArea.KNOWLEDGE

    override fun toolSpecs(): List<BuddyToolSpecDto> = listOf(LIST_OPEN_ESCALATIONS_SPEC, LIST_CANONICAL_ANSWERS_SPEC)

    override fun handles(toolName: String): Boolean =
        toolName == LIST_OPEN_ESCALATIONS || toolName == LIST_CANONICAL_ANSWERS

    override fun execute(call: BuddyToolCallDto, context: TeamToolContext): String =
        when (call.name) {
            LIST_OPEN_ESCALATIONS -> openEscalations(context.projectId)
            LIST_CANONICAL_ANSWERS -> canonicalAnswers(context.projectId)
            else -> "Unknown tool: ${call.name}."
        }

    private fun openEscalations(projectId: UUID): String {
        val open = knowledgeBaseService.listOpen(projectId)
        if (open.isEmpty()) {
            return "Nobody on this project has a question waiting for an answer."
        }
        return buildString {
            appendLine("Questions waiting for an answer on this project, oldest first:")
            open.forEach { request ->
                append("- “${request.question}” [request_id: ${request.id}]")
                request.hire?.let { hire ->
                    append(" — asked by ${hire.displayName}")
                    hire.currentPhase?.let { append(", who is currently in $it") }
                }
                appendLine(" (asked ${request.createdAt.atZone(ZoneOffset.UTC).toLocalDate()})")
            }
        }.trim()
    }

    /** Full text, not excerpts: editing an answer means reading all of it first. */
    private fun canonicalAnswers(projectId: UUID): String {
        val answers = knowledgeBaseService.listAnswers(projectId)
        if (answers.isEmpty()) {
            return "Nobody has written a canonical answer on this project yet."
        }
        return buildString {
            appendLine("Canonical answers on this project, most recently changed first:")
            answers.forEach { answer ->
                appendLine("- Q: ${answer.question} [answer_id: ${answer.id}]")
                appendLine("  A: ${answer.answer}")
            }
        }.trim()
    }

    companion object {
        const val LIST_OPEN_ESCALATIONS = "list_open_escalations"
        const val LIST_CANONICAL_ANSWERS = "list_canonical_answers"

        private fun noArgs() = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { })
        }

        val LIST_OPEN_ESCALATIONS_SPEC = BuddyToolSpecDto(
            name = LIST_OPEN_ESCALATIONS,
            description = "The questions hires on this project escalated because neither the docs nor the " +
                "canonical answers covered them, oldest first, each with its request_id, who asked, and where " +
                "they are in onboarding. Use it for 'what are people stuck on?' or before answering or " +
                "dismissing one. Takes no arguments — it always reads this project.",
            parameters = noArgs(),
        )

        val LIST_CANONICAL_ANSWERS_SPEC = BuddyToolSpecDto(
            name = LIST_CANONICAL_ANSWERS,
            description = "The answers the team has written down on this project, with their full text and " +
                "answer_id. Read it before editing one, and quote what is there rather than your memory of it. " +
                "Takes no arguments — it always reads this project.",
            parameters = noArgs(),
        )
    }
}
