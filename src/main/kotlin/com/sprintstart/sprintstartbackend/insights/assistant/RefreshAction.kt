package com.sprintstart.sprintstartbackend.insights.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.TeamActionDraft
import com.sprintstart.sprintstartbackend.assistant.external.TeamActionHandler
import com.sprintstart.sprintstartbackend.assistant.external.TeamArea
import com.sprintstart.sprintstartbackend.assistant.external.TeamToolContext
import com.sprintstart.sprintstartbackend.assistant.external.stringFields
import kotlinx.serialization.json.JsonObject

/**
 * Rebuilding one of a project's derived insight views, offered in team mode's knowledge area: a bulk action
 * that takes no arguments and always proposes the same rebuild. A subclass names it and says how it ran.
 *
 * @property label The confirm button.
 * @property preview Everything the manager is agreeing to, in words.
 */
abstract class RefreshAction(
    name: String,
    description: String,
    private val label: String,
    private val preview: String,
) : TeamActionHandler {
    override val area = TeamArea.KNOWLEDGE
    override val risk = BuddyProposalRisk.BULK
    override val spec = BuddyToolSpecDto(
        name = name,
        description = description,
        parameters = stringFields(required = emptyList()),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft =
        TeamActionDraft.Proposed(params = JsonObject(emptyMap()), label = label, preview = preview)

    /** A refresh rebuilds from what is there at the confirm, so nothing can have gone stale since the offer. */
    override fun recheck(params: JsonObject, context: TeamToolContext): String? = null
}
