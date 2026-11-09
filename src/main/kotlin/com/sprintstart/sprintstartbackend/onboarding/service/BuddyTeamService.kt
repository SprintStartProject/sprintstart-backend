package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySessionFilters
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyTeamMessage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyTeamSession
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toAgentMessage
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyMessageResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyTeamMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyTeamSessionRepository
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Team mode: a project manager's buddy conversation about one project's team.
 *
 * Every entry point confirms through [UserApi.canManageProject] that the caller manages the project. Each
 * project has its own conversation, separate from the manager's onboarding memory. Opening a visit runs the
 * protocol in [BuddyOpeningStream]; a reply runs [TeamAgentLoop], which rebuilds the tool list on every hop
 * because `open_area` changes what the next hop may call.
 */
@Service
class BuddyTeamService(
    private val buddyTeamSessionRepository: BuddyTeamSessionRepository,
    private val buddyTeamMessageRepository: BuddyTeamMessageRepository,
    private val openingStream: BuddyOpeningStream,
    private val buddyTeamTools: BuddyTeamTools,
    private val teamAgentLoop: TeamAgentLoop,
    private val userApi: UserApi,
    private val compactionScheduler: BuddyCompactionScheduler,
) {
    /**
     * Returns the current visit of the caller's team conversation about [projectId], oldest first.
     *
     * @throws ResponseStatusException 404 if the user does not exist; 403 if they do not manage the project.
     */
    fun getMessagesForMe(authId: String, projectId: UUID): List<BuddyMessageResponse> {
        val userId = authorize(authId, projectId)
        val session = buddyTeamSessionRepository.findByUserIdAndProjectId(userId, projectId)
            ?: return emptyList()
        return buddyTeamMessageRepository
            .findAllBySessionIdOrderByCreatedAtAsc(session.id)
            .sinceLastOpening { it.opening }
            .map { it.toResponse() }
    }

    /**
     * Opens a team-mode visit, streaming a greeting grounded in the team's attention list.
     *
     * Behaves like [BuddyService.streamOpenForMe]: an existing greeting is replayed, a broken stream
     * keeps what was read, and an empty one persists nothing.
     *
     * @throws ResponseStatusException 404 if the user does not exist; 403 if they do not manage the project.
     */
    fun streamOpenForMe(authId: String, projectId: UUID): Flow<BuddyStreamEvent> {
        val userId = authorize(authId, projectId)
        val session = getOrCreateSession(userId, projectId)
        val all = buddyTeamMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)

        all.lastOrNull()?.takeIf { it.opening }?.let { return openingStream.replay(it.content) }

        val request = BuddyOpenRequest(
            memory = session.summary,
            recent = all.drop(session.summarizedCount).map { it.toAgentMessage() },
            state = buddyTeamTools.teamSnapshot(projectId),
            teamMode = true,
        )
        return openingStream.open(
            request,
            OpeningConversation(
                fallbackGreeting = FALLBACK_TEAM_OPENING,
                saveOpening = { greeting -> saveOpening(session, greeting) },
                afterOpening = { compactionScheduler.foldTeamConversation(UserId(userId), ProjectId(projectId)) },
            ),
        )
    }

    /**
     * Sends the manager's message in team mode and streams the reply.
     *
     * With [capabilitiesEnabled] false no tools are mounted. The [filters] are sent on every hop, because
     * the AI service applies them on whichever hop the model searches.
     *
     * @throws ResponseStatusException 404 if the user does not exist; 403 if they do not manage the project.
     */
    suspend fun sendMessageForMe(
        authId: String,
        projectId: UUID,
        content: String,
        capabilitiesEnabled: Boolean = true,
        filters: BuddySessionFilters? = null,
        currentPage: String? = null,
    ): Flow<BuddyStreamEvent> {
        val userId = authorize(authId, projectId)
        val session = getOrCreateSession(userId, projectId)

        val transcript = buddyTeamMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
        val history = transcript.drop(session.summarizedCount).map { it.toAgentMessage() }
        val carriedOver = transcript.areasStillOpen()

        buddyTeamMessageRepository.save(
            BuddyTeamMessage(session = session, role = BuddyMessageRole.USER, content = content),
        )

        return flow {
            val turn = TeamAgentTurn(
                context = TeamToolContext(
                    userId = userId,
                    authId = authId,
                    projectId = projectId,
                    currentPage = currentPage,
                ),
                capabilitiesEnabled = capabilitiesEnabled,
                filters = filters,
                priorSummary = session.summary,
                messages = history + BuddyAgentMessageDto(role = BuddyAgentMessageDto.ROLE_USER, content = content),
                areas = OpenAreas(carriedOver),
            )

            try {
                with(teamAgentLoop) { runTurn(turn) }
            } catch (e: Exception) {
                saveIncompleteReply(session, turn.emittedContent.toString(), turn.areas)
                throw e
            }

            buddyTeamMessageRepository.save(
                BuddyTeamMessage(
                    session = session,
                    role = BuddyMessageRole.ASSISTANT,
                    content = turn.emittedContent.toString(),
                    openedAreas = turn.areas.activeThisTurn.encoded(),
                ),
            )
            compactionScheduler.foldTeamConversation(UserId(userId), ProjectId(projectId))
        }.endFailureWithErrorEvent()
    }

    /** Keeps a turn that was stopped or failed mid-answer, so the manager's read shows what they saw. */
    private fun saveIncompleteReply(session: BuddyTeamSession, content: String, areas: OpenAreas) {
        if (content.isBlank()) {
            return
        }

        buddyTeamMessageRepository.save(
            BuddyTeamMessage(
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = content,
                openedAreas = areas.activeThisTurn.encoded(),
                isIncomplete = true,
            ),
        )
    }

    private fun saveOpening(session: BuddyTeamSession, greeting: String) {
        buddyTeamMessageRepository.save(
            BuddyTeamMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = greeting, opening = true),
        )
    }

    private fun getOrCreateSession(userId: UUID, projectId: UUID): BuddyTeamSession =
        buddyTeamSessionRepository.findByUserIdAndProjectId(userId, projectId)
            ?: buddyTeamSessionRepository.save(BuddyTeamSession(userId = userId, projectId = projectId))

    /** @throws ResponseStatusException 404 if the user does not exist; 403 if they do not manage the project. */
    private fun authorize(authId: String, projectId: UUID): UUID {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }
        if (!userApi.canManageProject(authId, projectId)) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Team mode is only available for a project you manage, and you do not manage project $projectId.",
            )
        }
        return userId
    }

    private companion object {
        const val FALLBACK_TEAM_OPENING =
            "You're in team mode. Ask me who needs your attention, or how somebody on the team is getting on."
    }
}
