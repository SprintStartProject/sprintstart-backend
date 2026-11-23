package com.sprintstart.sprintstartbackend.starterwork.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.HireSnapshotOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireTool
import com.sprintstart.sprintstartbackend.assistant.external.HireToolContext
import com.sprintstart.sprintstartbackend.assistant.external.HireToolOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireToolSuggestion
import com.sprintstart.sprintstartbackend.assistant.external.noArgumentsSchema
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.starterwork.external.HireStarterWorkApi
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** The hire's competency ledger, for the Buddy: what they have shown, and where they are below target. */
@Component
@Order(HireToolOrder.COMPETENCIES)
class MyCompetenciesTool(
    private val hireStarterWork: HireStarterWorkApi,
) : HireTool {
    override val spec = SPEC

    override val snapshotPosition = HireSnapshotOrder.COMPETENCIES

    /** Names the ledger, so it cannot be mistaken for the path chip. */
    override val suggestion = HireToolSuggestion(
        label = "What have I shown?",
        question = "What have I shown so far — where does my ledger stand?",
    )

    override fun execute(call: BuddyToolCallDto, context: HireToolContext): String = describe(context.hire)

    override fun snapshotFor(hire: UserId): String = "Competencies:\n" + describe(hire)

    /**
     * Level-0 rows are placed-but-unknown, not evidence of a skill, so they are left out, as the skills rail
     * leaves them out: the buddy never reports a competency the hire has not shown.
     */
    private fun describe(hire: UserId): String {
        val ledger = hireStarterWork.competenciesOf(hire).filter { it.level > 0 }
        if (ledger.isEmpty()) {
            return "You have no demonstrated competencies on your ledger yet — that's normal early on."
        }
        val (held, inProgress) = ledger.partition { it.level >= it.targetLevel }
        return buildString {
            appendLine("Competencies held (meet their target level): ${held.size}")
            held.forEach { appendLine("- ${it.label} (level ${it.level}/${it.targetLevel})") }
            if (inProgress.isNotEmpty()) {
                appendLine("Below target (progress made, not yet met): ${inProgress.size}")
                inProgress.forEach { appendLine("- ${it.label} (level ${it.level}/${it.targetLevel})") }
            }
        }.trim()
    }

    companion object {
        const val GET_MY_COMPETENCIES = "get_my_competencies"

        val SPEC = BuddyToolSpecDto(
            name = GET_MY_COMPETENCIES,
            description = "The hire's own competency ledger: which skills they have demonstrated " +
                "(and at what level vs the target), and which they have made progress toward but " +
                "not yet met. Use this for questions about where the hire stands or what they have " +
                "shown, e.g. 'where do I stand?' or 'what have I proven so far?'. Takes no " +
                "arguments — it always reads the caller.",
            parameters = noArgumentsSchema(),
        )
    }
}

/**
 * The competencies nobody has evidence for yet, for the Buddy: what a short assessment would be about.
 *
 * Named topics rather than a count, because the mentor has to *ask about* them. Each carries its key, and the
 * key is how the placement is recorded, the same suggestion→claim shape the suggested tasks use, so the
 * thing discussed and the thing written cannot drift apart. Which ones the available work needs is marked
 * rather than filtered: a hire may be good at something nothing in the pool touches.
 *
 * Mounted only while something is still unplaced: a mentor handed an empty topic list finds a way to offer
 * the assessment anyway.
 */
@Component
@Order(HireToolOrder.COMPETENCIES_TO_ASSESS)
class CompetenciesToAssessTool(
    private val hireStarterWork: HireStarterWorkApi,
) : HireTool {
    override val spec = SPEC

    override val snapshotPosition = HireSnapshotOrder.UNPLACED

    override fun isMountedFor(hire: UserId): Boolean = hireStarterWork.placementTopicsFor(hire).isNotEmpty()

    override fun execute(call: BuddyToolCallDto, context: HireToolContext): String {
        val topics = hireStarterWork.placementTopicsFor(context.hire)
        if (topics.isEmpty()) {
            return "There's nothing left to place — every competency this team tracks already has " +
                "something behind it for this hire."
        }
        return buildString {
            appendLine(
                "Nothing is known about this hire on these yet. Ask about a few of them in " +
                    "conversation — never all of them — and record each with record_assessment:",
            )
            topics.forEach { topic ->
                append("- ${topic.label} [competency_key: ${topic.key}]")
                if (topic.neededByAvailableWork) append(" (work they could claim right now needs this)")
                appendLine()
                topic.description?.let { appendLine("    · $it") }
            }
        }.trim()
    }

    /**
     * Written for the greeting rather than reusing the tool's text, which is addressed to a reasoner holding
     * tools: the opener has none, and "record each with record_assessment" would put a tool name in front of
     * the hire.
     */
    override fun snapshotFor(hire: UserId): String? {
        val topics = hireStarterWork.placementTopicsFor(hire)
        if (topics.isEmpty()) return null
        val named = topics.take(GREETING_TOPICS).joinToString(", ") { it.label }
        return "Never placed on (a short chat would settle these, and you may offer one): $named"
    }

    companion object {
        const val GET_COMPETENCIES_TO_ASSESS = "get_competencies_to_assess"

        /** A greeting is 2-4 sentences; a dozen skill names in one of them is a list, not a welcome. */
        const val GREETING_TOPICS = 3

        val SPEC = BuddyToolSpecDto(
            name = GET_COMPETENCIES_TO_ASSESS,
            description = "The competencies this team tracks that nothing is known about for this " +
                "hire yet, each with the competency_key to record a placement by, and a note when " +
                "work they could claim right now needs it. Read this before offering the hire a " +
                "quick assessment, and read it again before asking about anything — it is the only " +
                "list of skills that exist here, so never invent a skill that is not on it. Ask " +
                "about a few in conversation, not all of them, and record each one with " +
                "record_assessment as you go. Takes no arguments — it always reads the caller.",
            parameters = noArgumentsSchema(),
        )
    }
}
