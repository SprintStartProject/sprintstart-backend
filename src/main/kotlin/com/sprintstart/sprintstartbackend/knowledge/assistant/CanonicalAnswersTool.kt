package com.sprintstart.sprintstartbackend.knowledge.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.HireTool
import com.sprintstart.sprintstartbackend.assistant.external.HireToolContext
import com.sprintstart.sprintstartbackend.assistant.external.HireToolOrder
import com.sprintstart.sprintstartbackend.knowledge.external.HireKnowledgeApi
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** The answers teammates wrote for questions the docs could not answer, searched for the Buddy. */
@Component
@Order(HireToolOrder.CANONICAL_ANSWERS)
class CanonicalAnswersTool(
    private val hireKnowledgeApi: HireKnowledgeApi,
) : HireTool {
    override val spec = SPEC

    override fun execute(call: BuddyToolCallDto, context: HireToolContext): String {
        val query = call.textArgument("query")
        if (query.isBlank()) return "No search query was provided."
        val matches = hireKnowledgeApi.searchAnswers(context.hire, query)
        if (matches.isEmpty()) {
            return "No teammate has answered anything like this yet."
        }
        return matches.joinToString("\n\n") { "Q: ${it.question}\nA: ${it.answer}" }
    }

    companion object {
        const val SEARCH_CANONICAL_ANSWERS = "search_canonical_answers"

        val SPEC = BuddyToolSpecDto(
            name = SEARCH_CANONICAL_ANSWERS,
            description = "Durable answers a teammate previously gave to questions the buddy could " +
                "not answer from the docs — how-we-do-things, conventions, tribal knowledge. These " +
                "are authoritative and human-written: prefer them, and quote them faithfully. " +
                "Search here alongside the docs for any 'how do we…' or 'what's our…' question. " +
                "If neither the docs nor these cover it, say so plainly so the hire can escalate.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "What the hire is asking, as a search query.")
                    }
                }
                putJsonArray("required") { add("query") }
            },
        )
    }
}
