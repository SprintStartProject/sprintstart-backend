package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.CreateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.UpdateOnboardingResourceRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component
import java.net.URI

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

/** Offers to attach a link to a step of a member's path. */
@Component
class AddResourceAction(
    private val scope: ContentScope,
    private val onboardingResourceService: OnboardingResourceService,
) : TeamActionHandler {
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

        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("step_id", target.element.id.toString())
                put("title", title)
                put("url", url)
                put("description", description)
            },
            label = "Add link “${title.forLabel()}”",
            preview = buildString {
                appendLine("Attach a link to “${target.element.title}” on ${target.owner.displayName}'s path:")
                appendLine("“$title” — $url")
                if (description.isNotEmpty()) appendLine(description)
                scope.sharedNote(target.owner, context.projectId).takeIf { it.isNotEmpty() }?.let { append("\n$it") }
            }.trim(),
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        scope.missing(PathElementKind.STEP, params.uuid("step_id"), context.projectId)

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        onboardingResourceService.createOnboardingResourceForStepId(
            requireNotNull(params.uuid("step_id")),
            CreateOnboardingResourceRequest(
                title = params.text("title"),
                description = params.text("description"),
                url = params.text("url"),
            ),
        )
        return "Added. “${params.text("title")}” is on the step now."
    }
}

/** Offers to change a link on a member's path. */
@Component
class UpdateResourceAction(
    private val scope: ContentScope,
    private val onboardingResourceService: OnboardingResourceService,
) : TeamActionHandler {
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
        val current = onboardingResourceService.getOnboardingResourceById(target.element.id)

        val url = call.changedText("url", current.url, allowBlank = false)
        if (url != null && !isWebLink(url)) return TeamActionDraft.Refused(NOT_A_WEB_LINK)

        val changes = ChangeSet("resource_id", current.id)
        changes.add("title", call.changedText("title", current.title, allowBlank = false)) {
            "Title: “${current.title}” becomes “$it”"
        }
        changes.add("url", url) { "Address: ${current.url} becomes $it" }
        changes.add("description", call.changedText("description", current.description)) {
            "Description becomes: ${it.ifEmpty { "(empty)" }}"
        }
        if (changes.isEmpty) return TeamActionDraft.Refused("Nothing would change: that is already how the link is.")

        return TeamActionDraft.Proposed(
            params = changes.params(),
            label = "Change link “${current.title.forLabel()}”",
            preview = "Change the link “${current.title}” on ${target.owner.displayName}'s path:\n" +
                changes.preview() +
                scope.sharedNote(target.owner, context.projectId).let { if (it.isEmpty()) "" else "\n\n$it" },
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        scope.missing(PathElementKind.RESOURCE, params.uuid("resource_id"), context.projectId)

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val id = requireNotNull(params.uuid("resource_id"))
        val current = onboardingResourceService.getOnboardingResourceById(id)
        onboardingResourceService.updateOnboardingResourceById(
            id,
            UpdateOnboardingResourceRequest(
                title = params.text("title").ifEmpty { current.title },
                description = if (params.containsKey(
                        "description",
                    )
                ) {
                    params.text("description")
                } else {
                    current.description
                },
                url = params.text("url").ifEmpty { current.url },
            ),
        )
        return "Done. The link is updated."
    }
}

/** Offers to remove a link from a member's path. */
@Component
class DeleteResourceAction(
    private val scope: ContentScope,
    private val onboardingResourceService: OnboardingResourceService,
) : TeamActionHandler {
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

        return TeamActionDraft.Proposed(
            params = buildJsonObject { put("resource_id", resource.id.toString()) },
            label = "Remove link “${resource.title.forLabel()}”",
            preview = buildString {
                append("Remove the link “${resource.title}” from ${target.owner.displayName}'s onboarding path. ")
                append("This cannot be undone.")
                scope.sharedNote(target.owner, context.projectId).takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
            }.trim(),
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        scope.missing(PathElementKind.RESOURCE, params.uuid("resource_id"), context.projectId)

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        onboardingResourceService.deleteOnboardingResourceById(requireNotNull(params.uuid("resource_id")))
        return "Removed. The link is gone."
    }
}
