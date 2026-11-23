package com.sprintstart.sprintstartbackend.teamspace.service

import com.sprintstart.sprintstartbackend.arrival.external.ArrivalReadApi
import com.sprintstart.sprintstartbackend.arrival.external.enums.Rigor
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.TeamToolContext
import com.sprintstart.sprintstartbackend.insights.external.InsightsReadApi
import com.sprintstart.sprintstartbackend.insights.model.dto.response.metrics.AttentionSeverity
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.starterwork.external.HireStarterWorkApi
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The three team reads that are always mounted: who needs attention, who is on the project, and how one
 * member is getting on.
 *
 * Every read checks that the person it describes belongs to the turn's project.
 */
@Component
class TeamReadTools(
    private val insightsRead: InsightsReadApi,
    private val hireStarterWork: HireStarterWorkApi,
    private val arrivalSteps: ArrivalReadApi,
    private val projectMembershipApi: ProjectMembershipApi,
) {
    fun toolSpecs(): List<BuddyToolSpecDto> = listOf(
        GET_TEAM_ATTENTION_SPEC,
        FIND_MEMBER_SPEC,
        GET_MEMBER_PROGRESS_SPEC,
    )

    /** @return A plain-text result for the model, or `null` when [call] is not a team read. */
    fun execute(call: BuddyToolCallDto, context: TeamToolContext): String? =
        when (call.name) {
            GET_TEAM_ATTENTION -> teamAttention(context.projectId)
            FIND_MEMBER -> findMember(context.projectId, call.textArgument("query"))
            GET_MEMBER_PROGRESS -> memberProgress(context.projectId, call.textArgument("member_id"))
            else -> null
        }

    /** The text a greeting grounds itself in; the same read as `get_team_attention`, so they cannot differ. */
    fun teamSnapshot(projectId: UUID): String = "Who needs attention:\n${teamAttention(projectId)}"

    /** Each line says whose move it is: a pull request waiting on review is the reviewer's, not the author's. */
    private fun teamAttention(projectId: UUID): String {
        val attention = insightsRead.attentionOn(ProjectId(projectId))
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
            appendLine(
                if (needle.isBlank()) "Everyone on this project:" else "On this project, matching “$needle”:",
            )
            matches.forEach { member ->
                append("- ${member.displayName} [member_id: ${member.userId}]")
                member.githubLogin?.let { append(" (GitHub: $it)") }
                appendLine()
            }
        }.trim()
    }

    /** Refuses anybody who is not a member of the project: the check that makes a model-supplied id safe. */
    private fun memberProgress(projectId: UUID, rawMemberId: String): String {
        val memberId = runCatching { UUID.fromString(rawMemberId.trim()) }.getOrNull()
            ?: return "No valid member_id was given. Call find_member first and pass the member_id it returns."
        val member = projectMembershipApi.getProjectMembers(projectId).firstOrNull { it.userId == memberId }
            ?: return "That person is not on this project, so their progress cannot be read here."

        return listOf(
            "Member: ${member.displayName}",
            "Before they can work:\n${arrivalFor(memberId, projectId)}",
            "Contributions on this project:\n${contributionsFor(memberId, projectId)}",
            "Competencies:\n${competenciesFor(memberId)}",
        ).joinToString("\n\n")
    }

    /**
     * Read with [ArrivalReadApi.forHireOn], not by filtering the member's full list: another of their
     * projects can override a company step there, which would hide a step that still applies here.
     * Like the hire's own tool, it gives no total and never calls a step blocking.
     */
    private fun arrivalFor(memberId: UUID, projectId: UUID): String {
        val steps = arrivalSteps.forHireOn(UserId(memberId), ProjectId(projectId))
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
                    append("- ${resolved.title}")
                    if (!resolved.selfConfirmable) {
                        append(" [confirmed by the system; they cannot mark it done themselves]")
                    }
                    appendLine()
                }
            }
            if (settled.isNotEmpty()) {
                appendLine("Already settled:")
                settled.forEach { resolved ->
                    val how = if (resolved.rigor == Rigor.OBSERVED) "we confirmed this" else "they told us"
                    appendLine("- ${resolved.title} ($how)")
                }
            }
        }.trim()
    }

    private fun contributionsFor(memberId: UUID, projectId: UUID): String {
        val timeline = insightsRead.hireTimeline(UserId(memberId), ProjectId(projectId))
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
            val stall = if (timeline.stalled) "yes${timeline.stalledReason?.let { " ($it)" }.orEmpty()}" else "no"
            appendLine("- Stalled: $stall")
            appendLine("- Pull requests sent back for changes: ${timeline.returnedContributionCount}")
        }.trim()
    }

    /** Level-0 rows are placed-but-unknown, not evidence, so they are excluded as on the hire's own tool. */
    private fun competenciesFor(memberId: UUID): String {
        val ledger = hireStarterWork.competenciesOf(UserId(memberId)).filter { it.level > 0 }
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

    companion object {
        const val GET_TEAM_ATTENTION = "get_team_attention"
        const val FIND_MEMBER = "find_member"
        const val GET_MEMBER_PROGRESS = "get_member_progress"

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
