package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationOrigin
import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationStep
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.orientation.AuthorOrientationCitationRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.orientation.AuthorOrientationRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.orientation.AuthorOrientationSectionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.orientation.OrientationPacketResponse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.stereotype.Component
import java.util.UUID

/*
 * The orientation-packet actions of team mode's content area.
 *
 * A packet is keyed by a starter-work task and a project, but the service behind it only checks that
 * the task exists. The task's source is what ties it to a project, so both actions resolve the task
 * through ContentScope — its repository must be linked to the turn's project — at proposal and at
 * confirm.
 *
 * A packet somebody wrote is served exactly as written and never regenerated, so replacing or dropping
 * one throws that person's text away. The previews say so, and a confirm is turned down if the packet
 * was replaced since the preview.
 */

private const val PACKET_CHANGED =
    "The packet for that task changed since this was proposed, so nothing was changed. Call " +
        "get_orientation_packet and offer it again."

private val ORIENTATION_STEPS = OrientationStep.entries.map { it.name }

private fun orientationStepOf(text: String): OrientationStep? =
    OrientationStep.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }

/** Who wrote a packet, as the previews say it. */
internal fun OrientationOrigin.author(): String = if (this ==
    OrientationOrigin.HUMAN
) {
    "written by a person"
} else {
    "assembled by the AI"
}

/** What identifies the packet a preview was written against; changes whenever the packet is replaced. */
private fun OrientationPacketResponse?.version(): String = this?.assembledAt?.toString().orEmpty()

private fun currentPacket(service: TaskOrientationService, taskId: UUID, projectId: UUID): OrientationPacketResponse? =
    service.getForAuthoring(taskId, projectId).packet

/** The sections the model gave, or the reason they cannot be used. */
private fun readSections(raw: List<JsonObject>): Pair<List<AuthorOrientationSectionRequest>?, String?> {
    if (raw.isEmpty()) return null to "A packet needs at least one section."
    val sections = raw.mapIndexed { index, item ->
        val step = orientationStepOf(item.text("step"))
            ?: return null to "Section ${index + 1} needs a step: ${ORIENTATION_STEPS.joinToString(", ")}."
        if (item.text("title").isEmpty() || item.text("body").isEmpty()) {
            return null to "Section ${index + 1} needs both a title and a body."
        }
        val citations = item.objectArray("citations").map { citation ->
            val url = citation.text("source_url")
            if (url.isNotEmpty() &&
                !isWebLink(url)
            ) {
                return null to "Section ${index + 1} has a link that is not a web address."
            }
            if (citation.text("filename").isEmpty()) return null to "Section ${index + 1} has a source with no name."
            AuthorOrientationCitationRequest(citation.text("filename"), url.takeIf { it.isNotEmpty() })
        }
        AuthorOrientationSectionRequest(step, item.text("title"), item.text("body"), citations)
    }
    return sections to null
}

private fun AuthorOrientationRequest.stored(): JsonObject =
    buildJsonObject {
        summary?.let { put("summary", it) }
        putJsonArray("sections") {
            sections.forEach { section ->
                add(
                    buildJsonObject {
                        put("step", section.step.name)
                        put("title", section.title)
                        put("body", section.body)
                        putJsonArray("citations") {
                            section.citations.forEach { citation ->
                                add(
                                    buildJsonObject {
                                        put("filename", citation.filename)
                                        citation.sourceUrl?.let { put("source_url", it) }
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

/** The sections as the hire will read them, in full — what the manager is agreeing to is the text. */
private fun AuthorOrientationRequest.readable(): String =
    buildString {
        summary?.let { appendLine("Summary: $it") }
        sections.forEach { section ->
            appendLine()
            appendLine("${section.step.name.lowercase().replace('_', ' ')} — ${section.title}")
            appendLine(section.body)
            section.citations.forEach {
                appendLine(
                    "  source: ${it.filename}${it.sourceUrl?.let { url -> " <$url>" }.orEmpty()}",
                )
            }
        }
    }.trim()

private fun orientationSectionSchema() =
    buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("step") {
                put("type", "string")
                put("description", "Which step of the path to a first pull request this section belongs to.")
                putJsonArray("enum") { ORIENTATION_STEPS.forEach { add(it) } }
            }
            putJsonObject("title") { put("type", "string") }
            putJsonObject("body") { put("type", "string") }
            putJsonObject("citations") {
                put("type", "array")
                put("description", "Optional sources to point at.")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("filename") { put("type", "string") }
                        putJsonObject("source_url") { put("type", "string") }
                    }
                }
            }
        }
    }

/** Offers to write the orientation packet a hire gets for a starter-work task. */
@Component
class AuthorOrientationPacketAction(
    private val scope: ContentScope,
    private val taskOrientationService: TaskOrientationService,
) : TeamActionHandler {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "author_orientation_packet",
        description = "Offer to write the orientation packet hires get for a starter-work task on this project, " +
            "replacing whatever packet is there. Once written it is served exactly as written and the AI never " +
            "rewrites it. Call get_orientation_packet first and start from what is there. Use the task_id from " +
            "list_starter_work_pool. This does NOT save anything by itself; the manager confirms the full text.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("task_id") {
                    put("type", "string")
                    put("description", "The task_id from list_starter_work_pool.")
                }
                putJsonObject("summary") {
                    put("type", "string")
                    put("description", "Optional. A short overview shown above the sections.")
                }
                putJsonObject("sections") {
                    put("type", "array")
                    put("description", "At least one, in reading order.")
                    put("items", orientationSectionSchema())
                }
            }
            putJsonArray("required") {
                add("task_id")
                add("sections")
            }
        },
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val task = scope.proposal(call.uuidArgument("task_id"), context.projectId)
            ?: return TeamActionDraft.Refused(TASK_NOT_HERE)
        val (sections, problem) = readSections(call.arguments.objectArray("sections"))
        if (sections == null) return TeamActionDraft.Refused(problem.orEmpty())
        val request = AuthorOrientationRequest(call.textArgument("summary").takeIf { it.isNotEmpty() }, sections)
        val existing = currentPacket(taskOrientationService, task.id, context.projectId)

        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("task_id", task.id.toString())
                // What the packet was when this was written, so a confirm can tell it was replaced since.
                put("base_version", existing.version())
                put("packet", request.stored())
            },
            label = "Write the orientation for “${task.title.forLabel()}”",
            preview = buildString {
                appendLine("Write the orientation packet for “${task.title}” on this project. Hires read this:")
                appendLine()
                appendLine(request.readable())
                appendLine()
                existing?.let {
                    appendLine("It replaces the packet that is there now, ${it.origin.author()}.")
                } ?: appendLine("There is no packet for this task yet.")
                append("Once saved it is served exactly as written; the AI does not regenerate it.")
            }.trim(),
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val task = scope.proposal(params.uuid("task_id"), context.projectId) ?: return TASK_NOT_HERE
        val now = currentPacket(taskOrientationService, task.id, context.projectId).version()
        return PACKET_CHANGED.takeIf { now != params.text("base_version") }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val packet = params["packet"] as JsonObject
        val (sections, _) = readSections(packet.objectArray("sections"))
        taskOrientationService.authorPacket(
            requireNotNull(params.uuid("task_id")),
            context.projectId,
            AuthorOrientationRequest(packet.text("summary").takeIf { it.isNotEmpty() }, requireNotNull(sections)),
        )
        return "Saved. Hires get exactly this text for the task."
    }
}

/** Offers to drop a task's orientation packet. */
@Component
class RevertOrientationPacketAction(
    private val scope: ContentScope,
    private val taskOrientationService: TaskOrientationService,
) : TeamActionHandler {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.DESTRUCTIVE
    override val spec = BuddyToolSpecDto(
        name = "revert_orientation_packet",
        description = "Offer to drop the orientation packet for a starter-work task on this project, so the " +
            "next hire who opens it gets one assembled from the docs again. If a person wrote the packet, " +
            "their text is deleted. Use the task_id from list_starter_work_pool. This does NOT drop anything " +
            "by itself; the manager confirms.",
        parameters = stringFields(
            "task_id" to "The task_id from list_starter_work_pool.",
            required = listOf("task_id"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val task = scope.proposal(call.uuidArgument("task_id"), context.projectId)
            ?: return TeamActionDraft.Refused(TASK_NOT_HERE)
        val existing = currentPacket(taskOrientationService, task.id, context.projectId)
            ?: return TeamActionDraft.Refused("There is no packet for that task, so there is nothing to drop.")

        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("task_id", task.id.toString())
                put("base_version", existing.version())
            },
            label = "Drop the orientation for “${task.title.forLabel()}”",
            preview = buildString {
                appendLine(
                    "Drop the orientation packet for “${task.title}” on this project, ${existing.origin.author()}.",
                )
                appendLine()
                if (existing.origin == OrientationOrigin.HUMAN) {
                    appendLine("A person wrote it, and their text is deleted. This cannot be undone.")
                }
                append("The next hire who opens the task gets a packet assembled from the docs again.")
            }.trim(),
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val task = scope.proposal(params.uuid("task_id"), context.projectId) ?: return TASK_NOT_HERE
        val now = currentPacket(taskOrientationService, task.id, context.projectId).version()
        return PACKET_CHANGED.takeIf { now != params.text("base_version") }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        taskOrientationService.revertToAi(requireNotNull(params.uuid("task_id")), context.projectId)
        return "Dropped. The next hire gets a packet assembled from the docs."
    }
}
