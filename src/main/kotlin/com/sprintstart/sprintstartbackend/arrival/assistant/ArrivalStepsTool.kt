package com.sprintstart.sprintstartbackend.arrival.assistant

import com.sprintstart.sprintstartbackend.arrival.external.ArrivalReadApi
import com.sprintstart.sprintstartbackend.arrival.external.enums.Rigor
import com.sprintstart.sprintstartbackend.arrival.model.response.arrival.ArrivalStepResponse
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.HireSnapshotOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireTool
import com.sprintstart.sprintstartbackend.assistant.external.HireToolContext
import com.sprintstart.sprintstartbackend.assistant.external.HireToolOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireToolSuggestion
import com.sprintstart.sprintstartbackend.assistant.external.noArgumentsSchema
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * The hire's arrival steps, for the Buddy: what has to be true before they can work, and what is settled.
 *
 * Mounted only when a step applies to the hire, read from the same steps the board card shows, so the two
 * cannot disagree. The part a checklist cannot do is the reason this is a tool and not just a card: a hire
 * can ask *why* a step exists, say they are stuck on one, and be told who to chase.
 *
 * Two things this text refuses to say. No total and no fraction: "2 of 5 done" over a mix of what the
 * system observed and what the hire told us is meaningless, and a model handed such a number repeats it.
 * And nothing is described as blocking: an outstanding step never stopped anybody claiming work or asking
 * a question, and saying otherwise here would put the gate back in the model's own words.
 */
@Component
@Order(HireToolOrder.ARRIVAL)
class ArrivalStepsTool(
    private val arrivalSteps: ArrivalReadApi,
) : HireTool {
    override val spec = SPEC

    override val suggestion = HireToolSuggestion(
        label = "What do I still need?",
        question = "What do I still need to get set up?",
    )

    override val snapshotPosition = HireSnapshotOrder.ARRIVAL

    override fun isMountedFor(hire: UserId): Boolean = arrivalSteps.forHire(hire).isNotEmpty()

    override fun execute(call: BuddyToolCallDto, context: HireToolContext): String = describe(context.hire)

    /** Setup comes before progress in a greeting: one grounded in progress greets a hire who cannot get in. */
    override fun snapshotFor(hire: UserId): String? =
        ("Before they can work:\n" + describe(hire)).takeIf { isMountedFor(hire) }

    private fun describe(hire: UserId): String {
        val steps = arrivalSteps.forHire(hire)
        if (steps.isEmpty()) {
            return "Nobody has written an arrival list for this hire, so there is nothing outstanding."
        }
        val (settled, outstanding) = steps.partition { it.settled }
        return buildString {
            appendOutstanding(outstanding)
            appendSettled(settled)
        }.trim()
    }

    private fun StringBuilder.appendOutstanding(outstanding: List<ArrivalStepResponse>) {
        if (outstanding.isEmpty()) {
            appendLine("Nothing is outstanding — every arrival step is settled.")
            return
        }
        appendLine("Still outstanding (none of this stops them working):")
        outstanding.forEach { resolved ->
            append("- ${resolved.title}")
            // Which project's step this is, for the same reason the card needs a heading:
            // a hire on two projects can be told "get staging access" twice, and a mentor
            // that cannot say which one is asking them to guess.
            resolved.projectName?.let { append(" (on $it)") }
            resolved.description?.let { append(" — $it") }
            resolved.href?.let { append(" ($it)") }
            // Whether the hire can settle it themselves decides what the buddy may offer.
            // Offering to tick a step the backend refuses would end in an error the hire
            // never caused.
            if (!resolved.selfConfirmable) {
                append(" [we check this one ourselves; they cannot mark it done]")
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendSettled(settled: List<ArrivalStepResponse>) {
        if (settled.isEmpty()) return
        appendLine()
        appendLine("Already settled:")
        settled.forEach { resolved ->
            val how = if (resolved.rigor == Rigor.OBSERVED) "we confirmed this" else "they told us"
            appendLine("- ${resolved.title} ($how)")
        }
    }

    companion object {
        const val GET_ARRIVAL_STEPS = "get_arrival_steps"

        val SPEC = BuddyToolSpecDto(
            name = GET_ARRIVAL_STEPS,
            description = "What has to be true before this hire can work — accounts, access, a " +
                "working setup — and which of it they have settled. Read this before " +
                "suggesting a task: somebody still waiting on an access grant does not need a " +
                "good first task, they need the access. Use it for 'what do I still need to do?', " +
                "'how do I get set up?', or whenever they say they are stuck getting started. " +
                "None of it blocks them — an outstanding step is a thing to chase, never a " +
                "reason they may not claim work or ask you something. Never total the steps up or " +
                "give a fraction: what the system confirmed and what they told us are different " +
                "facts. Takes no arguments — it always reads the caller.",
            parameters = noArgumentsSchema(),
        )
    }
}
