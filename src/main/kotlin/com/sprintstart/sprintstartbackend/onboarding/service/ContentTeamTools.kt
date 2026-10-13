package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.response.orientation.OrientationPacketResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionForAdminResponse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.time.ZoneOffset
import java.util.UUID

private const val SHORT_CHARS = 200

private fun StepStatus.said(): String = name.lowercase().replace('_', ' ')

private fun String.short(): String = if (length <= SHORT_CHARS) this else take(SHORT_CHARS - 1).trimEnd() + "…"

/** Questions with the ids and correct answers a replacement has to start from. */
private fun renderChecks(questions: List<QuestionForAdminResponse>): String =
    buildString {
        questions.forEachIndexed { index, question ->
            appendLine(
                "${index + 1}. ${question.question} [question_id: ${question.id}] — " +
                    question.type.name
                        .lowercase()
                        .replace('_', ' '),
            )
            question.correctAnswer?.let { appendLine("   Correct answer: $it") }
            question.options.forEach {
                appendLine("   ${if (it.correct) "[correct]" else "[wrong]  "} ${it.label} [option_id: ${it.id}]")
            }
            question.explanation?.let { appendLine("   Explanation: $it") }
        }
    }.trim()

/** A packet in full: replacing it means starting from every word of it. */
private fun renderPacket(packet: OrientationPacketResponse): String =
    buildString {
        appendLine("Packet for “${packet.taskTitle}”, ${packet.origin.author()}.")
        packet.summary?.let { appendLine("Summary: $it") }
        packet.sections.forEach { section ->
            appendLine()
            appendLine("${section.step.name.lowercase().replace('_', ' ')} — ${section.title}")
            appendLine(section.body)
            section.citations.forEach {
                appendLine(
                    "  source: ${it.filename}${it.sourceUrl?.let { u -> " <$u>" }.orEmpty()}",
                )
            }
        }
    }.trim()

/**
 * The read tools of team mode's content area: what is on the onboarding paths of the project's
 * members, and what waits for the manager to answer.
 *
 * Paths belong to people, not projects, so everything here starts from a member of the turn's project
 * and reads only what is on that member's path — or, for an orientation packet, from a task whose
 * repository is linked to the project. The ids these print are how the content actions name their
 * targets, and every action checks again that its target is in scope.
 */
@Component
class ContentTeamTools(
    private val scope: ContentScope,
    private val pathElements: PathElements,
    private val onboardingPathService: OnboardingPathService,
    private val onboardingStepService: OnboardingStepService,
    private val onboardingSkipService: OnboardingSkipService,
    private val onboardingFeedbackService: OnboardingFeedbackService,
    private val questionAttemptService: QuestionAttemptService,
    private val taskOrientationService: TaskOrientationService,
) : TeamAreaTools {
    override val area = TeamArea.CONTENT

    override fun toolSpecs(): List<BuddyToolSpecDto> = SPECS

    override fun handles(toolName: String): Boolean = SPECS.any { it.name == toolName }

    override fun execute(call: BuddyToolCallDto, context: TeamToolContext): String =
        when (call.name) {
            GET_MEMBER_PATH -> memberPath(call.uuidArgument("member_id"), context.projectId)
            LIST_PENDING_SKIPS -> pendingSkips(context.projectId)
            LIST_FEEDBACK -> feedback(call.booleanArgument("include_read") == true, context.projectId)
            GET_PHASE_CHECKS -> phaseChecks(call.uuidArgument("phase_id"), context.projectId)
            GET_ORIENTATION_PACKET -> orientationPacket(call.uuidArgument("task_id"), context.projectId)
            else -> "Unknown tool: ${call.name}."
        }

    /**
     * One member's whole path, phases to tasks, each with the id an action needs.
     *
     * Descriptions are cut short: the ids are what a manager's request is turned into, and the
     * full text of every step of a long path would bury them.
     */
    private fun memberPath(memberId: UUID?, projectId: UUID): String {
        val owner = scope.member(memberId, projectId) ?: return NOT_A_MEMBER_HERE
        val path = runCatching { onboardingPathService.getOnboardingPathByUserId(owner.userId) }
            .getOrElse {
                if (it !is ResponseStatusException) throw it
                return "${owner.displayName} has no onboarding path yet."
            }

        return buildString {
            appendLine("${owner.displayName}'s onboarding path:")
            scope.sharedNote(owner, projectId).takeIf { it.isNotEmpty() }?.let { appendLine(it) }
            path.phases.sortedBy { it.position }.forEach { phase ->
                appendLine()
                appendLine("Phase ${phase.position + 1}: ${phase.title} [phase_id: ${phase.id}]")
                phase.description.takeIf { it.isNotBlank() }?.let { appendLine("  ${it.short()}") }
                onboardingStepService.getOnboardingStepsByPhaseId(phase.id).sortedBy { it.position }.forEach { step ->
                    appendLine(
                        "  Step ${step.position + 1}: ${step.title} [step_id: ${step.id}] — " +
                            "${step.type.name.lowercase()}, ${step.estimatedMinutes} min, ${step.status.said()}",
                    )
                    step.description.takeIf { it.isNotBlank() }?.let { appendLine("    ${it.short()}") }
                    step.tasks.sortedBy { it.position }.forEach { task ->
                        appendLine("    - [${if (task.finished) "x" else " "}] ${task.title} [task_id: ${task.id}]")
                    }
                    step.resources.forEach { resource ->
                        appendLine("    - link: ${resource.title} <${resource.url}> [resource_id: ${resource.id}]")
                    }
                }
            }
            if (path.generationIssues.isNotEmpty()) {
                appendLine()
                appendLine("Not shown to them, because generating them produced nothing usable:")
                path.generationIssues.forEach {
                    appendLine(
                        "- ${it.title} [phase_id: ${it.phaseId}] (${it.status.name.lowercase().replace('_', ' ')})",
                    )
                }
            }
        }.trim()
    }

    /** Requests to skip a step that nobody has answered, from members of this project only. */
    private fun pendingSkips(projectId: UUID): String {
        val pending = scope.members(projectId).flatMap { member ->
            onboardingSkipService
                .getAllSkipsByUserId(member.userId)
                .filter { it.status == SkipStatus.PENDING }
                .map { member to it }
        }
        if (pending.isEmpty()) return "Nobody on this project has a skip request waiting for an answer."

        return buildString {
            appendLine("Skip requests waiting for an answer, oldest first:")
            pending.sortedBy { it.second.createdAt }.forEach { (member, skip) ->
                val step = pathElements.find(PathElementKind.STEP, skip.stepId)
                append("- ${member.displayName} asks to skip “${step?.title ?: "a step"}”")
                step?.stepStatus?.let { append(" (${it.said()})") }
                appendLine(" [skip_id: ${skip.id}]")
                appendLine("  asked ${skip.createdAt.atZone(ZoneOffset.UTC).toLocalDate()}: “${skip.reason}”")
            }
        }.trim()
    }

    /** Feedback hires left on their onboarding, unread first — the read ones only when asked for. */
    private fun feedback(includeRead: Boolean, projectId: UUID): String {
        val all = scope.members(projectId).flatMap { member ->
            onboardingFeedbackService.getAllFeedbackByUserId(member.userId).map { member to it }
        }
        val shown = all.filter { includeRead || !it.second.read }
        if (shown.isEmpty()) {
            return if (all.isEmpty()) {
                "Nobody on this project has left onboarding feedback."
            } else {
                "There is no unread feedback. Pass include_read to see the ${all.size} already read."
            }
        }

        return buildString {
            appendLine("Onboarding feedback from this project's members, oldest first:")
            shown.sortedBy { it.second.createdAt }.forEach { (member, item) ->
                val about = item.stepTitle?.let { "“$it”" } ?: "their path as a whole"
                appendLine(
                    "- ${member.displayName} on $about [feedback_id: ${item.id}]${if (item.read) " (read)" else ""}",
                )
                appendLine("  ${item.createdAt.atZone(ZoneOffset.UTC).toLocalDate()}: “${item.message}”")
            }
        }.trim()
    }

    /** A phase's knowledge-check questions, with correct answers — the starting point of any replacement. */
    private fun phaseChecks(phaseId: UUID?, projectId: UUID): String {
        val target =
            scope.element(PathElementKind.PHASE, phaseId, projectId) ?: return notInScope(PathElementKind.PHASE)
        val questions = questionAttemptService.getPhaseQuestions(target.element.id).questions
        if (questions.isEmpty()) return "“${target.element.title}” has no knowledge-check questions."
        return "Knowledge checks of “${target.element.title}” on ${target.owner.displayName}'s path:\n" +
            renderChecks(questions.sortedBy { it.position })
    }

    /** The orientation packet a hire gets for a task, or the fact that there is none. */
    private fun orientationPacket(taskId: UUID?, projectId: UUID): String {
        val task = scope.proposal(taskId, projectId) ?: return TASK_NOT_HERE
        val packet = taskOrientationService.getForAuthoring(task.id, projectId).packet
            ?: return "There is no orientation packet for “${task.title}” on this project yet. One is assembled " +
                "from the docs the first time a hire opens the task, or a person can write it."
        return renderPacket(packet)
    }

    companion object {
        const val GET_MEMBER_PATH = "get_member_path"
        const val LIST_PENDING_SKIPS = "list_pending_skips"
        const val LIST_FEEDBACK = "list_feedback"
        const val GET_PHASE_CHECKS = "get_phase_checks"
        const val GET_ORIENTATION_PACKET = "get_orientation_packet"

        private fun noArgs() =
            buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject { })
            }

        private val SPECS = listOf(
            BuddyToolSpecDto(
                name = GET_MEMBER_PATH,
                description = "One project member's whole onboarding path: its phases, their steps, and each " +
                    "step's tasks and links, every one with the id an action needs. Also says where each step " +
                    "stands for them. Use the member_id from find_member. A path belongs to the person, not to " +
                    "this project, so it shows what they have across all of theirs.",
                parameters = stringFields(
                    "member_id" to "The member_id from find_member.",
                    required = listOf("member_id"),
                ),
            ),
            BuddyToolSpecDto(
                name = LIST_PENDING_SKIPS,
                description = "The requests to skip a step that hires on this project are waiting to have " +
                    "answered, each with the reason the hire gave and a skip_id. Read one before offering to " +
                    "accept or deny it. Takes no arguments.",
                parameters = noArgs(),
            ),
            BuddyToolSpecDto(
                name = LIST_FEEDBACK,
                description = "Feedback hires on this project left on their onboarding, each with a " +
                    "feedback_id. Unread only unless include_read is true. Use it for 'what are hires saying " +
                    "about onboarding?'; quote them rather than summarising away what they said.",
                parameters = toolFields(
                    ToolField("include_read", "True to include feedback already marked as read.", "boolean"),
                    required = emptyList(),
                ),
            ),
            BuddyToolSpecDto(
                name = GET_PHASE_CHECKS,
                description = "The knowledge-check questions at the end of one phase of a member's path, with " +
                    "their correct answers and ids. Read it before offering replace_phase_checks, which takes " +
                    "the whole list. Use the phase_id from get_member_path.",
                parameters = stringFields(
                    "phase_id" to "The phase_id from get_member_path.",
                    required = listOf("phase_id"),
                ),
            ),
            BuddyToolSpecDto(
                name = GET_ORIENTATION_PACKET,
                description = "The orientation packet hires get for one starter-work task on this project, in " +
                    "full, and whether a person or the AI wrote it. Read it before offering to write or drop " +
                    "one. Use the task_id from list_starter_work_pool.",
                parameters = stringFields(
                    "task_id" to "The task_id from list_starter_work_pool.",
                    required = listOf("task_id"),
                ),
            ),
        )
    }
}
