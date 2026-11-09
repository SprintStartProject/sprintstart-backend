package com.sprintstart.sprintstartbackend.teamspace.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.ChangeLines
import com.sprintstart.sprintstartbackend.assistant.external.TeamActionDraft
import com.sprintstart.sprintstartbackend.assistant.external.TeamArea
import com.sprintstart.sprintstartbackend.assistant.external.TeamToolContext
import com.sprintstart.sprintstartbackend.assistant.external.ToolField
import com.sprintstart.sprintstartbackend.assistant.external.TypedTeamAction
import com.sprintstart.sprintstartbackend.assistant.external.UuidSerializer
import com.sprintstart.sprintstartbackend.assistant.external.changedText
import com.sprintstart.sprintstartbackend.assistant.external.forLabel
import com.sprintstart.sprintstartbackend.assistant.external.stringFields
import com.sprintstart.sprintstartbackend.assistant.external.toolFields
import com.sprintstart.sprintstartbackend.path.external.StepAuthoringApi
import com.sprintstart.sprintstartbackend.path.external.model.PathElementKind
import com.sprintstart.sprintstartbackend.path.model.request.resource.CreateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.path.model.request.resource.UpdateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.shared.ids.ResourceId
import com.sprintstart.sprintstartbackend.shared.ids.StepId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component
import java.net.URI
import java.util.UUID

/*
 * The resource actions of team mode's content area.
 *
 * A resource is a link a person opens from their step. The services store whatever URL they are given,
 * which is fine behind an admin form and not fine behind a model: a link the hire will click has to
 * be a web link, so these refuse anything else at proposal.
 */

internal const val NOT_A_WEB_LINK = "The url must be a full web address starting with http:// or https://."

/** Whether [text] is an absolute http(s) address with a host. */
internal fun isWebLink(text: String): Boolean =
    runCatching { URI(text) }.getOrNull()?.let {
        (it.scheme == "http" || it.scheme == "https") && !it.host.isNullOrBlank()
    } ?: false

/** The link to attach to a step. */
@Serializable
data class AddResourceParams(
    @SerialName("step_id") @Serializable(with = UuidSerializer::class) val stepId: UUID,
    val title: String,
    val url: String,
    val description: String,
)

/** The edits a confirmed link update applies; a null field is left as the link has it by then. */
@Serializable
data class UpdateResourceParams(
    @SerialName("resource_id") @Serializable(with = UuidSerializer::class) val resourceId: UUID,
    val title: String? = null,
    val url: String? = null,
    val description: String? = null,
)

/** The link a confirmed removal deletes. */
@Serializable
data class DeleteResourceParams(
    @SerialName("resource_id") @Serializable(with = UuidSerializer::class) val resourceId: UUID,
)

/** Offers to attach a link to a step of a member's path. */
@Component
class AddResourceAction(
    private val scope: ContentScope,
    private val stepAuthoringApi: StepAuthoringApi,
) : TypedTeamAction<AddResourceParams>(AddResourceParams.serializer()) {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "add_resource",
        description = "Offer to attach a link to a step of a member's onboarding path. Use the step_id from " +
            "get_member_path. This does NOT add anything by itself; the manager confirms.",
        parameters = toolFields(
            ToolField("step_id", "The step_id from get_member_path."),
            ToolField("title", "What the link is called."),
            ToolField("url", "The full web address, starting with http:// or https://."),
            ToolField("description", "What the person will find there."),
            required = listOf("step_id", "title", "url"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val target = scope.element(PathElementKind.STEP, call.uuidArgument("step_id"), context.projectId)
            ?: return TeamActionDraft.Refused(notInScope(PathElementKind.STEP))
        val title = call.textArgument("title")
        if (title.isEmpty()) return TeamActionDraft.Refused("A resource needs a title.")
        val url = call.textArgument("url")
        if (!isWebLink(url)) return TeamActionDraft.Refused(NOT_A_WEB_LINK)
        val description = call.textArgument("description")

        return proposed(
            params = AddResourceParams(target.element.id, title, url, description),
            label = "Add link “${title.forLabel()}”",
            preview = buildString {
                appendLine("Attach a link to “${target.element.title}” on ${target.owner.displayName}'s path:")
                appendLine("“$title” — $url")
                if (description.isNotEmpty()) appendLine(description)
                scope.sharedNote(target.owner, context.projectId).takeIf { it.isNotEmpty() }?.let { append("\n$it") }
            }.trim(),
        )
    }

    override fun recheckParams(params: AddResourceParams, context: TeamToolContext): String? =
        scope.missing(PathElementKind.STEP, params.stepId, context.projectId)

    override suspend fun performParams(params: AddResourceParams, context: TeamToolContext): String {
        stepAuthoringApi.createResource(
            StepId(params.stepId),
            CreateOnboardingResourceRequest(
                title = params.title,
                description = params.description,
                url = params.url,
            ),
        )
        return "Added. “${params.title}” is on the step now."
    }
}

/** Offers to change a link on a member's path. */
@Component
class UpdateResourceAction(
    private val scope: ContentScope,
    private val stepAuthoringApi: StepAuthoringApi,
) : TypedTeamAction<UpdateResourceParams>(UpdateResourceParams.serializer()) {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "update_resource",
        description = "Offer to change a link on a member's onboarding path: its title, its address or its " +
            "description. Pass only what should change. Use the resource_id from get_member_path. This does " +
            "NOT change anything by itself; the manager confirms.",
        parameters = toolFields(
            ToolField("resource_id", "The resource_id from get_member_path."),
            ToolField("title", "The new title."),
            ToolField("url", "The new full web address, starting with http:// or https://."),
            ToolField("description", "The new description."),
            required = listOf("resource_id"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val target = scope.element(PathElementKind.RESOURCE, call.uuidArgument("resource_id"), context.projectId)
            ?: return TeamActionDraft.Refused(notInScope(PathElementKind.RESOURCE))
        val current = stepAuthoringApi.resource(ResourceId(target.element.id))

        val url = call.changedText("url", current.url, allowBlank = false)
        if (url != null && !isWebLink(url)) return TeamActionDraft.Refused(NOT_A_WEB_LINK)

        val newTitle = call.changedText("title", current.title, allowBlank = false)
        val newDescription = call.changedText("description", current.description)

        val changes = ChangeLines()
        changes.add(newTitle) { "Title: “${current.title}” becomes “$it”" }
        changes.add(url) { "Address: ${current.url} becomes $it" }
        changes.add(newDescription) { "Description becomes: ${it.ifEmpty { "(empty)" }}" }
        if (changes.isEmpty) return TeamActionDraft.Refused("Nothing would change: that is already how the link is.")

        return proposed(
            params = UpdateResourceParams(current.id, newTitle, url, newDescription),
            label = "Change link “${current.title.forLabel()}”",
            preview = "Change the link “${current.title}” on ${target.owner.displayName}'s path:\n" +
                changes.preview() +
                scope.sharedNote(target.owner, context.projectId).let { if (it.isEmpty()) "" else "\n\n$it" },
        )
    }

    override fun recheckParams(params: UpdateResourceParams, context: TeamToolContext): String? =
        scope.missing(PathElementKind.RESOURCE, params.resourceId, context.projectId)

    override suspend fun performParams(params: UpdateResourceParams, context: TeamToolContext): String {
        val id = params.resourceId
        val current = stepAuthoringApi.resource(ResourceId(id))
        stepAuthoringApi.updateResource(
            ResourceId(id),
            UpdateOnboardingResourceRequest(
                title = params.title ?: current.title,
                description = params.description ?: current.description,
                url = params.url ?: current.url,
            ),
        )
        return "Done. The link is updated."
    }
}

/** Offers to remove a link from a member's path. */
@Component
class DeleteResourceAction(
    private val scope: ContentScope,
    private val stepAuthoringApi: StepAuthoringApi,
) : TypedTeamAction<DeleteResourceParams>(DeleteResourceParams.serializer()) {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.DESTRUCTIVE
    override val spec = BuddyToolSpecDto(
        name = "delete_resource",
        description = "Offer to remove a link from a member's onboarding path. Use the resource_id from " +
            "get_member_path. This does NOT remove anything by itself; the manager confirms.",
        parameters = stringFields(
            "resource_id" to "The resource_id from get_member_path.",
            required = listOf("resource_id"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val target = scope.element(PathElementKind.RESOURCE, call.uuidArgument("resource_id"), context.projectId)
            ?: return TeamActionDraft.Refused(notInScope(PathElementKind.RESOURCE))
        val resource = target.element

        return proposed(
            params = DeleteResourceParams(resource.id),
            label = "Remove link “${resource.title.forLabel()}”",
            preview = buildString {
                append("Remove the link “${resource.title}” from ${target.owner.displayName}'s onboarding path. ")
                append("This cannot be undone.")
                scope.sharedNote(target.owner, context.projectId).takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
            }.trim(),
        )
    }

    override fun recheckParams(params: DeleteResourceParams, context: TeamToolContext): String? =
        scope.missing(PathElementKind.RESOURCE, params.resourceId, context.projectId)

    override suspend fun performParams(params: DeleteResourceParams, context: TeamToolContext): String {
        stepAuthoringApi.deleteResource(ResourceId(params.resourceId))
        return "Removed. The link is gone."
    }
}
