package com.sprintstart.sprintstartbackend.teamspace.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyAppGuideTools
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.TeamArea
import com.sprintstart.sprintstartbackend.assistant.external.TeamAreaTools
import com.sprintstart.sprintstartbackend.assistant.external.TeamToolContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The tools the buddy may call in team mode, and the rule for which of them are mounted.
 *
 * Unlike the hire's buddy ([BuddyToolExecutor]), these tools read *other people* on the project. That is
 * safe because [BuddyTeamService] confirms the caller manages the project before every turn, and because
 * every tool checks that what it reads belongs to that project: a member id from the model is a pointer,
 * never an authorisation.
 *
 * The team reads ([TeamReadTools]) are always mounted. `open_area` is mounted only when some area has
 * tools, and an area's tools only once it has been opened. [execute] refuses any tool that was not
 * mounted for the hop it was called on.
 */
@Component
class BuddyTeamTools(
    private val teamReadTools: TeamReadTools,
    private val areaToolsProvider: ObjectProvider<TeamAreaTools>,
    private val teamProposalService: TeamProposalService,
    private val buddyAppGuideTools: BuddyAppGuideTools,
) {
    private val areaTools: Map<TeamArea, TeamAreaTools> by lazy {
        areaToolsProvider.orderedStream().toList().associateBy { it.area }
    }

    private val openableAreas: Set<TeamArea> by lazy { areaTools.keys + teamProposalService.actionAreas() }

    private val actionAreaByName: Map<String, TeamArea> by lazy {
        TeamArea.entries
            .flatMap { area ->
                teamProposalService.actionSpecs(setOf(area)).map { it.name to area }
            }.toMap()
    }

    /**
     * The tools mounted for one hop, given the areas opened so far this turn.
     *
     * @param openedAreas Areas `open_area` has opened on earlier hops of this turn.
     * @return The team reads, `open_area` when any area has tools, and each opened area's read tools
     * and actions.
     */
    fun toolSpecs(openedAreas: Set<TeamArea>): List<BuddyToolSpecDto> =
        buildList {
            addAll(teamReadTools.toolSpecs())
            add(BuddyAppGuideTools.GET_APP_GUIDE_SPEC)
            if (openableAreas.isNotEmpty()) {
                add(openAreaSpec())
            }
            openedAreas.sorted().forEach { area ->
                areaTools[area]?.let { addAll(it.toolSpecs()) }
            }
            addAll(teamProposalService.actionSpecs(openedAreas))
        }

    /** The area [toolName] belongs to, as a read tool or an action; `null` for everything else. */
    fun areaOf(toolName: String): TeamArea? =
        areaTools.values.firstOrNull { it.handles(toolName) }?.area
            ?: actionAreaByName[toolName]

    /** Opens the area the model named; its tools are mounted from the next hop on. */
    fun openArea(call: BuddyToolCallDto): OpenAreaOutcome {
        val requested = call.textArgument("area")
        val area = TeamArea.entries
            .firstOrNull { it.name.equals(requested, ignoreCase = true) }
            ?.takeIf { it in openableAreas }
        if (area == null) {
            val available = openableAreas.sorted().joinToString(", ") { it.name.lowercase() }
            return OpenAreaOutcome(
                area = null,
                toolResult = "There is no area called “$requested”. Areas you can open: $available.",
            )
        }
        val names = toolNamesIn(area).joinToString(", ")
        return OpenAreaOutcome(
            area = area,
            toolResult = "Opened ${area.name.lowercase()}. These tools are available from your next step: $names.",
        )
    }

    private fun toolNamesIn(area: TeamArea): List<String> =
        (areaTools[area]?.toolSpecs().orEmpty() + teamProposalService.actionSpecs(setOf(area))).map { it.name }

    /**
     * Runs one team-mode tool for [context].
     *
     * @param mountedToolNames The names handed to the model on the hop this call came from.
     * @return A plain-text result for the model; a tool not in [mountedToolNames] is refused.
     */
    fun execute(call: BuddyToolCallDto, context: TeamToolContext, mountedToolNames: Set<String>): String {
        if (call.name !in mountedToolNames) {
            return "The tool ${call.name} is not available right now."
        }
        if (call.name == BuddyAppGuideTools.GET_APP_GUIDE) {
            return buddyAppGuideTools.guideFor(context.userId, context.currentPage)
        }
        return teamReadTools.execute(call, context)
            ?: areaTools.values.firstOrNull { it.handles(call.name) }?.execute(call, context)
            ?: "Unknown tool: ${call.name}."
    }

    /** The text a greeting grounds itself in; the same read as `get_team_attention`, so they cannot differ. */
    fun teamSnapshot(projectId: UUID): String = teamReadTools.teamSnapshot(projectId)

    private fun openAreaSpec(): BuddyToolSpecDto =
        BuddyToolSpecDto(
            name = OPEN_AREA,
            description = "Open one area of the manager's work so its tools become available on your next " +
                "step. Open an area only when the manager asks about something in it; its tools are not " +
                "available until you have opened it. An area you open or use stays open for your next few " +
                "replies, so you do not open it again to act on something you discussed. If a tool you need " +
                "is not there, open its area first, or call it by the name listed below: its area is opened " +
                "for you and you call it again on your next step. Never say something has been offered for " +
                "confirmation unless a tool of an opened area did it.\n\nThe areas, and the tools in each:\n" +
                openableAreas.sorted().joinToString("\n") {
                    "- ${it.name.lowercase()}: ${it.summary} (tools: ${toolNamesIn(it).joinToString(", ")})"
                },
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("area") {
                        put("type", "string")
                        putJsonArray("enum") { openableAreas.sorted().forEach { add(it.name.lowercase()) } }
                        put("description", "The area to open.")
                    }
                }
                putJsonArray("required") { add("area") }
            },
        )

    /** What opening an area produced: the area, when there was one, and what to tell the model. */
    data class OpenAreaOutcome(
        val area: TeamArea?,
        val toolResult: String,
    )

    companion object {
        const val OPEN_AREA = "open_area"
    }
}
