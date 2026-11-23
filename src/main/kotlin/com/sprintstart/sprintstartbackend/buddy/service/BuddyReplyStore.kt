package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.assistant.external.ArtifactLookupService
import com.sprintstart.sprintstartbackend.assistant.external.BuddyCitationDto
import com.sprintstart.sprintstartbackend.assistant.external.ResolvedBuddyCitation
import com.sprintstart.sprintstartbackend.buddy.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddyCitation
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddyMessage
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.buddy.repository.BuddyCitationRepository
import com.sprintstart.sprintstartbackend.buddy.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.shared.ids.parseUuidOrNull
import org.slf4j.LoggerFactory

/** Resolves and persists what a buddy reply cites and says, whole or cut short. */
internal class BuddyReplyStore(
    private val buddyMessageRepository: BuddyMessageRepository,
    private val buddyCitationRepository: BuddyCitationRepository,
    private val artifactLookupService: ArtifactLookupService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** The citations the AI named that still point at an artifact; the others are logged and dropped. */
    fun resolveCitations(citations: List<BuddyCitationDto>): List<ResolvedBuddyCitation> =
        citations.mapNotNull { citation ->
            val artifactId = citation.artifactId?.let(::parseUuidOrNull)
            val resolved = artifactId?.let(artifactLookupService::resolve)

            if (artifactId == null || resolved == null) {
                logger.warn(
                    "Could not resolve artifact {} for buddy citation",
                    citation.artifactId,
                )
                null
            } else {
                ResolvedBuddyCitation(
                    artifactId = artifactId,
                    filename = resolved.filename,
                    sourceUrl = resolved.sourceUrl,
                    startLine = citation.startLine,
                    startPage = citation.startPage,
                )
            }
        }

    /** Stores the finished reply and the citations it carries. */
    fun saveReply(session: BuddySession, content: String, citations: List<ResolvedBuddyCitation>) {
        val message = buddyMessageRepository.save(
            BuddyMessage(
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = content,
            ),
        )

        buddyCitationRepository.saveAll(
            citations.map { citation ->
                BuddyCitation(
                    artifactId = citation.artifactId,
                    filename = citation.filename,
                    sourceUrl = citation.sourceUrl,
                    startLine = citation.startLine,
                    startPage = citation.startPage,
                    message = message,
                )
            },
        )
    }

    /** Keeps what the hire had been shown of a reply that stopped early; stores nothing if nothing was shown. */
    fun saveIncompleteReply(session: BuddySession, content: String) {
        if (content.isBlank()) {
            return
        }

        buddyMessageRepository.save(
            BuddyMessage(
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = content,
                isIncomplete = true,
            ),
        )
    }
}
