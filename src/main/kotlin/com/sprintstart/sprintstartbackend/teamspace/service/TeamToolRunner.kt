package com.sprintstart.sprintstartbackend.teamspace.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEventType
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.TeamToolContext
import com.sprintstart.sprintstartbackend.assistant.external.toolUseEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Runs the tools the team-mode buddy calls during one turn, on behalf of [BuddyTeamService].
 *
 * It decides what a call means: an action becomes a stored proposal, `open_area` mounts an area from the
 * next hop, and a tool whose area is not open yet opens it. Events for the client go out through the
 * `emit` callback.
 */
@Component
class TeamToolRunner(
    private val buddyTeamTools: BuddyTeamTools,
    private val teamProposalService: TeamProposalService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Runs one tool the AI asked for.
     *
     * An action never runs here: it becomes a stored proposal the manager confirms, and the model is told
     * it was offered, not done. Anything not mounted on this hop is refused by [BuddyTeamTools].
     */
    suspend fun run(
        call: BuddyToolCallDto,
        context: TeamToolContext,
        mountedToolNames: Set<String>,
        areas: OpenAreas,
        emit: suspend (BuddyStreamEvent) -> Unit,
    ): String {
        openAreaOf(call, mountedToolNames, areas, emit)?.let { return it }
        if (call.name in mountedToolNames) {
            buddyTeamTools.areaOf(call.name)?.let { areas.use(it) }
        }
        if (call.name in mountedToolNames && teamProposalService.isAction(call.name)) {
            val outcome = teamProposalService.propose(call, context)
            outcome.proposal?.let { proposal ->
                emit(
                    BuddyStreamEvent(
                        type = BuddyStreamEventType.ACTION_PROPOSAL,
                        action = proposal.action,
                        label = proposal.label,
                        proposalId = proposal.id.toString(),
                        preview = proposal.preview,
                        risk = proposal.risk.name,
                    ),
                )
            }
            return outcome.toolResult
        }
        emit(toolUseEvent(call.name))
        if (call.name == BuddyTeamTools.OPEN_AREA && call.name in mountedToolNames) {
            val outcome = buddyTeamTools.openArea(call)
            outcome.area?.let { areas.open(it) }
            return outcome.toolResult
        }
        return buddyTeamTools.execute(call, context, mountedToolNames)
    }

    /**
     * Opens the area of a tool the model called before it was mounted, and asks it to call again.
     *
     * The call itself is not run: its arguments were written without the tool's description.
     *
     * @return What to tell the model, or `null` when the call is not for an unopened area.
     */
    private suspend fun openAreaOf(
        call: BuddyToolCallDto,
        mountedToolNames: Set<String>,
        areas: OpenAreas,
        emit: suspend (BuddyStreamEvent) -> Unit,
    ): String? {
        if (call.name in mountedToolNames || BuddyTeamTools.OPEN_AREA !in mountedToolNames) {
            return null
        }
        val area = buddyTeamTools.areaOf(call.name) ?: return null
        logger.info("Team buddy called {} before opening its area; opening {}", call.name, area)
        emit(toolUseEvent(BuddyTeamTools.OPEN_AREA))
        areas.open(area)
        return "${call.name} belongs to the ${area.name.lowercase()} area, which was not open. It is open now: " +
            "call ${call.name} again on your next step. Nothing has been done or offered yet."
    }

    /**
     * Opens the areas of tools the model wrote out as text, the same case [openAreaOf] handles for a real call.
     *
     * @return What to tell the model in place of the reply it wrote.
     */
    suspend fun openAreasWrittenOut(
        text: String,
        mountedToolNames: Set<String>,
        areas: OpenAreas,
        emit: suspend (BuddyStreamEvent) -> Unit,
    ): String {
        if (BuddyTeamTools.OPEN_AREA !in mountedToolNames) {
            return TOOL_CALL_WRITTEN_OUT
        }
        val opened = text
            .writtenOutToolNames()
            .filter { it !in mountedToolNames }
            .mapNotNull { name -> buddyTeamTools.areaOf(name)?.let { name to it } }
        if (opened.isEmpty()) {
            return TOOL_CALL_WRITTEN_OUT
        }
        emit(toolUseEvent(BuddyTeamTools.OPEN_AREA))
        opened.forEach { (_, area) -> areas.open(area) }
        val names = opened.joinToString(", ") { it.first }
        return "That reply was a tool call written out as text, so nothing ran and the manager has not seen an " +
            "answer. The area of $names is open now: call it properly on your next step, not as text."
    }
}
