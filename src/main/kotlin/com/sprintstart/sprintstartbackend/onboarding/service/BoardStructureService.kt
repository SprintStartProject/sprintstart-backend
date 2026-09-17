package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.Board
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardStructure
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardStructureLimits
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardStructurePayload
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardStructureResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardStructureRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Reads and writes how a hire has arranged their board.
 *
 * Deliberately a separate service from [BoardService], which is about *what is on* a board — it
 * ensures the right cards exist and reads their content live from half the onboarding module. This
 * one touches two repositories and a JSON blob, and none of that gets clearer for sitting inside a
 * seven-hundred-line class it shares nothing with.
 *
 * It is also the only part of the board a client may hand over wholesale, and keeping that at arm's
 * length from the card logic is worth something on its own: nothing here can create, move or remove
 * a card.
 */
@Service
class BoardStructureService(
    private val boardRepository: BoardRepository,
    private val boardStructureRepository: BoardStructureRepository,
    private val projectMembershipApi: ProjectMembershipApi,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * A board's arrangement, or the empty one.
     *
     * An unarranged board answers with an empty arrangement rather than a 404: "you have not
     * arranged this yet" is a normal state on somebody's first day, and a client that has to treat
     * it as an error will treat some real errors as it too.
     *
     * Returns null when the caller is not on the project, which the controller turns into the same
     * 404 the board read gives — a 403 would confirm that a given project exists.
     */
    @Transactional(readOnly = true)
    fun read(userId: UUID, projectId: UUID): BoardStructureResponse? {
        if (!isMember(userId, projectId)) return null

        val board = boardRepository.findByUserIdAndProjectId(userId, projectId)
            ?: return BoardStructureResponse(BoardStructurePayload(), null)
        val stored = boardStructureRepository.findByBoardId(board.id)
            ?: return BoardStructureResponse(BoardStructurePayload(), null)

        // A row this version cannot read answers exactly as an unarranged board does, `updatedAt`
        // included. Reporting "nothing is stored" alongside the moment it was last stored is two
        // answers to one question, and the client acts on the first of them.
        val decoded = decode(stored) ?: return BoardStructureResponse(BoardStructurePayload(), null)

        return BoardStructureResponse(decoded, stored.updatedAt)
    }

    /**
     * Replaces the arrangement.
     *
     * Creates the board if this hire has none yet, the way [BoardService] does on first read: a
     * client that arranges before it reads is doing nothing wrong, and refusing would make the
     * order of two unrelated calls matter.
     *
     * **Last write wins, on purpose.** Two tabs are the realistic conflict and neither can be
     * merged sensibly — an arrangement is a statement about the whole board, so half of one and
     * half of the other is an arrangement nobody made. The returned `updatedAt` is how a client
     * notices it lost.
     */
    @Transactional
    fun write(userId: UUID, projectId: UUID, payload: BoardStructurePayload): BoardStructureResponse? {
        if (!isMember(userId, projectId)) return null

        val encoded = json.encodeToString(BoardStructurePayload.serializer(), payload)
        // Checked before anything is created, and on the encoded form, because that is the thing
        // that gets stored. The field limits the controller enforces multiply; this is the one
        // number that does not.
        if (encoded.length > BoardStructureLimits.DOCUMENT_CHARS) {
            throw ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "That is a larger arrangement than a board can hold.",
            )
        }

        val board = boardRepository.findByUserIdAndProjectId(userId, projectId)
            ?: boardRepository.save(Board(userId = userId, projectId = projectId))

        // One statement, so that two tabs arranging the same board for the first time cannot both
        // insert. See [BoardStructureRepository.upsert]; `now` is what the row will carry, which is
        // why it is read here rather than taken from an entity this no longer loads.
        val now = Instant.now()
        boardStructureRepository.upsert(
            id = UUID.randomUUID(),
            boardId = board.id,
            payload = encoded,
            now = now,
        )

        return BoardStructureResponse(payload, now)
    }

    /**
     * A stored arrangement, or null when it cannot be read.
     *
     * Written by a client and stored as text, so a row from an older shape is a real possibility.
     * `ignoreUnknownKeys` handles a field that has since gone; a payload that cannot be parsed at
     * all answers as unarranged rather than failing the board read, because an arrangement is the
     * least important thing on the page and the worst thing it can do is take the page down with it.
     *
     * Logged, and only for the exception that means "this text is not that document". Answering as
     * unarranged is quiet by design and the next whole-document PUT overwrites the row, so without
     * a line naming the board there would be nothing left to find afterwards. Anything else thrown
     * here is a bug rather than an old row, and is left to propagate where it can still be seen.
     */
    private fun decode(stored: BoardStructure): BoardStructurePayload? =
        try {
            json.decodeFromString(BoardStructurePayload.serializer(), stored.payload)
        } catch (e: SerializationException) {
            logger.warn(
                "Board structure {} of board {} could not be read and was answered as unarranged; " +
                    "the next write replaces it",
                stored.id,
                stored.boardId,
                e,
            )
            null
        }

    private fun isMember(userId: UUID, projectId: UUID): Boolean =
        projectMembershipApi.getProjectMembers(projectId).any { it.userId == userId }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
