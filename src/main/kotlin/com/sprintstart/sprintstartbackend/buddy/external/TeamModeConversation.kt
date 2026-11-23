package com.sprintstart.sprintstartbackend.buddy.external

import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.buddy.model.request.buddy.SendBuddyMessageRequest
import com.sprintstart.sprintstartbackend.buddy.model.response.buddy.BuddyMessageResponse
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import kotlinx.coroutines.flow.Flow

/**
 * Team mode as the buddy's endpoints reach it, when a request names a project the caller manages.
 *
 * The buddy's endpoints serve both conversations on the same URLs, and a message names its mode in its
 * body, so the split cannot happen in routing. The buddy declares what it needs here and team mode
 * implements it, so the buddy does not depend on team mode.
 */
interface TeamModeConversation {
    /** The current visit of the caller's team conversation about [project]. */
    fun messages(authId: AuthId, project: ProjectId): List<BuddyMessageResponse>

    /** Opens, or replays the opening of, the caller's team conversation about [project]. */
    fun open(authId: AuthId, project: ProjectId): Flow<BuddyStreamEvent>

    /** Answers [message] in the caller's team conversation about [project]. */
    fun send(authId: AuthId, project: ProjectId, message: SendBuddyMessageRequest): Flow<BuddyStreamEvent>
}
