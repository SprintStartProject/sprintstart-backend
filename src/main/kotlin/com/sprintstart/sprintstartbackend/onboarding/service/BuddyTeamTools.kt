package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.AttentionSeverity
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The tools the buddy may call in team mode, and the rule for which of them are mounted.
 *
 * The hire's buddy reads only the caller ([BuddyToolExecutor]). Team mode is the one deliberate
 * exception: a project's manager asks about *other people* on that project. Two checks make that safe,
 * and neither lives in the prompt:
 *
 * - [BuddyTeamService] confirms the caller manages the project before every turn, so nobody else is
 *   ever handed these tools.
 * - Every tool here checks that the person or thing it reads belongs to that project. A member id
 *   the model passes is a pointer, never an authorisation.
 *
 * The team reads are always mounted. Everything else arrives by area: `open_area` is mounted only
 * when some area has tools at all ("absent, never empty"), and an area's tools only once it has been
 * opened. [execute] refuses any tool that was not mounted for the hop it was called on.
 */
@Suppress("TooManyFunctions") // One function per team read, plus the mounting rule and its helpers.
@Component
class BuddyTeamTools(
    private val projectAttentionService: ProjectAttentionService,
    private val onboardingMetricsService: OnboardingMetricsService,
    private val myCompetencyService: MyCompetencyService,
    private val arrivalStepService: ArrivalStepService,
    private val projectMembershipApi: ProjectMembershipApi,
    private val areaToolsProvider: ObjectProvider<TeamAreaTools>,
    private val buddyProposalService: BuddyProposalService,
) {
    // Resolved lazily: an area's tools may themselves depend on services that depend on this one.
    private val areaTools: Map<TeamArea, TeamAreaTools> by lazy {
        areaToolsProvider.orderedStream().toList().associateBy { it.area }
    }

    /** Every area with something behind it: read tools, actions, or both. */
    private fun openableAreas(): Set<TeamArea> = areaTools.keys + buddyProposalService.actionAreas()

    /**
     * The tools mounted for one hop, given the areas opened so far this turn.
     *
     * @param openedAreas Areas `open_area` has opened on earlier hops of this turn.
     * @return The team reads, `open_area` when any area has tools, and each opened area's read tools
     * and actions.
     */
    fun toolSpecs(openedAreas: Set<TeamArea>): List<BuddyToolSpecDto> =
        buildList {
            add(GET_TEAM_ATTENTION_SPEC)
            add(FIND_MEMBER_SPEC)
            add(GET_MEMBER_PROGRESS_SPEC)
            if (openableAreas().isNotEmpty()) {
                add(openAreaSpec())
            }
            openedAreas.sorted().forEach { area ->
                areaTools[area]?.let { addAll(it.toolSpecs()) }
            }
            addAll(buddyProposalService.actionSpecs(openedAreas))
        }

    /**
     * Opens the area the model named, so its tools are mounted from the next hop on.
     *
     * @return The area opened, or `null` with the reason when no such area has tools.
     */
    fun openArea(call: BuddyToolCallDto): OpenAreaOutcome {
        val requested = call.stringArg("area").trim()
        val area = TeamArea.entries
            .firstOrNull { it.name.equals(requested, ignoreCase = true) }
            ?.takeIf { it in openableAreas() }
        if (area == null) {
            val available = openableAreas().sorted().joinToString(", ") { it.name.lowercase() }
            return OpenAreaOutcome(
                area = null,
                toolResult = "There is no area called “$requested”. Areas you can open: $available.",
            )
        }
        val names = (areaTools[area]?.toolSpecs().orEmpty() + buddyProposalService.actionSpecs(setOf(area)))
            .joinToString(", ") { it.name }
        return OpenAreaOutcome(
            area = area,
            toolResult = "Opened ${area.name.lowercase()}. These tools are available from your next step: $names.",
        )
    }

    /**
     * Runs one team-mode tool for [context], refusing any tool that was not mounted for this hop.
     *
     * @param mountedToolNames The names handed to the model on the hop this call came from.
     * @return A plain-text result for the model.
     */
    fun execute(call: BuddyToolCallDto, context: TeamToolContext, mountedToolNames: Set<String>): String {
        if (call.name !in mountedToolNames) {
            return "The tool ${call.name} is not available right now."
        }
        return when (call.name) {
            GET_TEAM_ATTENTION -> teamAttention(context.projectId)
            FIND_MEMBER -> findMember(context.projectId, call.stringArg("query"))
            GET_MEMBER_PROGRESS -> memberProgress(context.projectId, call.stringArg("member_id"))
            else -> areaTools.values.firstOrNull { it.handles(call.name) }?.execute(call, context)
                ?: "Unknown tool: ${call.name}."
        }
    }

    /**
     * A plain-text snapshot of the project's team for a team-mode greeting to ground itself in.
     *
     * Reuses [teamAttention] exactly, so the greeting and the tool can never describe different states.
     */
    fun teamSnapshot(projectId: UUID): String = "Who needs attention:\n" + teamAttention(projectId)

    /**
     * Who on the project is waiting or has stalled, most pressing first.
     *
     * Each line keeps the reason [ProjectAttentionService] wrote and says whose move it is. A pull
     * request waiting on review is the reviewer's move, and a manager told otherwise chases the one
     * person who cannot fix it.
     */
    private fun teamAttention(projectId: UUID): String {
        val attention = projectAttentionService.getAttention(projectId)
        if (attention.items.isEmpty()) {
            return "Nobody on this project (${attention.memberCount} members) is waiting on a review or has " +
                "stalled right now."
        }
        return buildString {
            appendLine("On this project (${attention.memberCount} members), most pressing first:")
            attention.items.forEach { item ->
                appendLine(
                    "- ${item.hireName} [member_id: ${item.hireId}]: ${item.reason} (${item.severity.whoseMove()})",
                )
            }
        }.trim()
    }

    private fun AttentionSeverity.whoseMove(): String =
        when (this) {
            AttentionSeverity.BLOCKED -> "waiting on somebody else's move, not theirs"
            AttentionSeverity.DRIFTING -> "no progress we can see yet"
        }

    /** The project's members matching [query] by name or account, or everyone when it is blank. */
    private fun findMember(projectId: UUID, query: String): String {
        val members = projectMembershipApi.getProjectMembers(projectId)
        if (members.isEmpty()) {
            return "Nobody is on this project yet."
        }
        val needle = query.trim()
        val matches = if (needle.isBlank()) {
            members
        } else {
            members.filter { member ->
                member.displayName.contains(needle, ignoreCase = true) ||
                    member.githubLogin?.contains(needle, ignoreCase = true) == true ||
                    member.jiraDisplayName?.contains(needle, ignoreCase = true) == true
            }
        }
        if (matches.isEmpty()) {
            return "Nobody on this project matches “$needle”. Call find_member without a query to list everyone."
        }
        return buildString {
            appendLine(if (needle.isBlank()) "Everyone on this project:" else "On this project, matching “$needle”:")
            matches.forEach { member ->
                append("- ${member.displayName} [member_id: ${member.userId}]")
                member.githubLogin?.let { append(" (GitHub: $it)") }
                appendLine()
            }
        }.trim()
    }

    /**
     * One member's onboarding on this project: arrival, contributions, competencies.
     *
     * Refuses anybody who is not a member of the project — the check that makes a model-supplied id
     * safe to accept at all.
     */
    private fun memberProgress(projectId: UUID, rawMemberId: String): String {
        val memberId = runCatching { UUID.fromString(rawMemberId.trim()) }.getOrNull()
            ?: return "No valid member_id was given. Call find_member first and pass the member_id it returns."
        val member = projectMembershipApi.getProjectMembers(projectId).firstOrNull { it.userId == memberId }
            ?: return "That person is not on this project, so their progress cannot be read here."

        return listOf(
            "Member: ${member.displayName}",
            "Before they can work:\n" + arrivalFor(memberId, projectId),
            "Contributions on this project:\n" + contributionsFor(memberId, projectId),
            "Competencies:\n" + competenciesFor(memberId),
        ).joinToString("\n\n")
    }

    /**
     * The member's arrival steps that apply on this project, outstanding and settled.
     *
     * Read with [ArrivalStepService.forHireOn], never by filtering the member's full list: that list
     * lets another of their projects override a company step, and narrowing it afterwards would hide
     * a step that still applies here. Same two refusals as the hire's own tool: no total, and nothing
     * described as blocking.
     */
    private fun arrivalFor(memberId: UUID, projectId: UUID): String {
        val steps = arrivalStepService.forHireOn(memberId, projectId)
        if (steps.isEmpty()) {
            return "No arrival steps apply to them on this project."
        }
        val (settled, outstanding) = steps.partition { it.settled }
        return buildString {
            if (outstanding.isEmpty()) {
                appendLine("Nothing is outstanding.")
            } else {
                appendLine("Still outstanding (none of this stops them working):")
                outstanding.forEach { resolved ->
                    append("- ${resolved.step.title}")
                    if (!resolved.step.selfConfirmable) {
                        append(" [confirmed by the system; they cannot mark it done themselves]")
                    }
                    appendLine()
                }
            }
            if (settled.isNotEmpty()) {
                appendLine("Already settled:")
                settled.forEach { resolved ->
                    val how = if (resolved.rigor == Rigor.OBSERVED) "we confirmed this" else "they told us"
                    appendLine("- ${resolved.step.title} ($how)")
                }
            }
        }.trim()
    }

    private fun contributionsFor(memberId: UUID, projectId: UUID): String {
        val timeline = onboardingMetricsService.getHireTimeline(memberId, projectId)
            ?: return "No onboarding metrics exist for them on this project yet."
        return buildString {
            appendLine("- Open pull requests: ${timeline.openContributionCount}")
            appendLine("- Merged pull requests: ${timeline.acceptedContributionCount}")
            timeline.longestOpenWaitHours?.let {
                appendLine("- Longest pull request currently waiting on a reviewer: $it hours")
            }
            timeline.hoursToFirstResponse?.let {
                appendLine("- Time from their first pull request to its first response: $it hours")
            }
            val stall = if (timeline.stalled) "yes" + (timeline.stalledReason?.let { " ($it)" } ?: "") else "no"
            appendLine("- Stalled: $stall")
            appendLine("- Pull requests sent back for changes: ${timeline.returnedContributionCount}")
            timeline.autonomyReachedAt?.let { appendLine("- Reached autonomy at: $it") }
        }.trim()
    }

    /** Level-0 rows are placed-but-unknown, not evidence, and are excluded as on the hire's own tool. */
    private fun competenciesFor(memberId: UUID): String {
        val ledger = myCompetencyService.getCompetenciesForUser(memberId).filter { it.level > 0 }
        if (ledger.isEmpty()) {
            return "Nothing demonstrated on their competency ledger yet — normal early on."
        }
        val (held, inProgress) = ledger.partition { it.level >= it.targetLevel }
        return buildString {
            appendLine("Meeting their target level: ${held.size}")
            held.forEach { appendLine("- ${it.label} (level ${it.level}/${it.targetLevel})") }
            if (inProgress.isNotEmpty()) {
                appendLine("Progress made, target not yet met: ${inProgress.size}")
                inProgress.forEach { appendLine("- ${it.label} (level ${it.level}/${it.targetLevel})") }
            }
        }.trim()
    }

    private fun openAreaSpec(): BuddyToolSpecDto =
        BuddyToolSpecDto(
            name = OPEN_AREA,
            description = "Open one area of the manager's work so its tools become available on your next " +
                "step. Open an area only when the manager asks about something in it; its tools are not " +
                "available until you have opened it. An area stays open for the manager's next message too, " +
                "and no longer: if they ask you to act on something from earlier and the tool is not there, " +
                "open the area again first. Never say something has been offered for confirmation unless a " +
                "tool of an opened area did it.\n\nThe areas:\n" +
                openableAreas().sorted().joinToString("\n") { "- ${it.name.lowercase()}: ${it.summary}" },
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("area") {
                        put("type", "string")
                        // The same set toolSpecs and openArea use. Listing only areas with read tools
                        // would mount open_area and still forbid the model from opening an area whose
                        // tools are all actions.
                        putJsonArray("enum") { openableAreas().sorted().forEach { add(it.name.lowercase()) } }
                        put("description", "The area to open.")
                    }
                }
                putJsonArray("required") { add("area") }
            },
        )

    private fun BuddyToolCallDto.stringArg(name: String): String =
        (arguments[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

    /** What opening an area produced: the area, when there was one, and what to tell the model. */
    data class OpenAreaOutcome(
        val area: TeamArea?,
        val toolResult: String,
    )

    companion object {
        const val GET_TEAM_ATTENTION = "get_team_attention"
        const val FIND_MEMBER = "find_member"
        const val GET_MEMBER_PROGRESS = "get_member_progress"
        const val OPEN_AREA = "open_area"

        val GET_TEAM_ATTENTION_SPEC = BuddyToolSpecDto(
            name = GET_TEAM_ATTENTION,
            description = "Who on this project is waiting on somebody else or has stalled, most pressing " +
                "first, each with the reason and a member_id. Use it for 'who needs my attention?' or " +
                "'is anyone stuck?'. Describe each person's situation as facts, never as a judgment of the " +
                "person: a pull request waiting on review is the reviewer's move. Takes no arguments — it " +
                "always reads the project this conversation is about.",
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject { })
            },
        )

        val FIND_MEMBER_SPEC = BuddyToolSpecDto(
            name = FIND_MEMBER,
            description = "The people on this project matching a name or account, each with their member_id. " +
                "Call it before get_member_progress whenever the manager names somebody, and pass the " +
                "member_id it returns — never guess an id. Without a query it lists everyone on the project.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Part of the person's name, GitHub login or Jira name. Optional.")
                    }
                }
            },
        )

        val GET_MEMBER_PROGRESS_SPEC = BuddyToolSpecDto(
            name = GET_MEMBER_PROGRESS,
            description = "One project member's onboarding on this project: what they still need before they " +
                "can work, their pull requests and whether any are waiting on a reviewer, and their " +
                "competencies. Use it for 'how far along is Sam?'. Pass the member_id from find_member or " +
                "get_team_attention. Report facts; never rank people against each other or call someone slow.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("member_id") {
                        put("type", "string")
                        put("description", "The member_id of the person, from find_member or get_team_attention.")
                    }
                }
                putJsonArray("required") { add("member_id") }
            },
        )
    }
}
