package com.sprintstart.sprintstartbackend.board.service

import com.sprintstart.sprintstartbackend.board.external.BoardCardVersion
import com.sprintstart.sprintstartbackend.board.external.BoardReading
import com.sprintstart.sprintstartbackend.board.external.enums.BoardActor
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardChange
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardState
import com.sprintstart.sprintstartbackend.board.external.model.HireBoard
import com.sprintstart.sprintstartbackend.board.model.entity.Board
import com.sprintstart.sprintstartbackend.board.model.entity.BoardCard
import com.sprintstart.sprintstartbackend.board.model.entity.BoardCardPayload
import com.sprintstart.sprintstartbackend.board.model.entity.ChecklistItemPayload
import com.sprintstart.sprintstartbackend.board.model.request.board.AuthoredCardRequest
import com.sprintstart.sprintstartbackend.board.model.request.board.ChecklistCardRequest
import com.sprintstart.sprintstartbackend.board.model.request.board.NoteCardRequest
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardCardResponse
import com.sprintstart.sprintstartbackend.board.repository.BoardCardRepository
import com.sprintstart.sprintstartbackend.board.repository.BoardRepository
import com.sprintstart.sprintstartbackend.shared.ids.BoardCardId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * What the hire's own notes, links and checklists say, and how the hire and the buddy change it.
 *
 * The hire edits whole cards; the buddy, only with the hire's confirm, may append, tick or reword
 * lines and replace a card it was shown. Every write is attributed to whoever made it.
 */
internal class BoardAuthoredCards(
    private val boardRepository: BoardRepository,
    private val boardCardRepository: BoardCardRepository,
    private val access: BoardAccess,
    private val hydrator: BoardCardHydrator,
) {
    private val json = boardPayloadJson

    /**
     * Adds a note, link or checklist to the hire's own board.
     *
     * Owned by the hire ([BoardCardOwner.HIRE]) whoever wrote it, which makes it theirs to edit and
     * to throw away. Several of these are allowed, unlike every other kind.
     *
     * [by] says who wrote it. One the buddy wrote — behind a confirm the hire pressed — is still
     * theirs, and is dated with [BoardCard.placedAt] as well, so the board says "your buddy put this
     * here" about exactly the cards it did.
     *
     * @throws ResponseStatusException 404 when they are not a member of that project, 400 when the
     * content is empty.
     */
    fun addAuthoredCard(
        userId: UserId,
        projectId: ProjectId,
        request: AuthoredCardRequest,
        by: BoardActor = BoardActor.HIRE,
    ): BoardCardResponse {
        val member = access.memberOrNull(userId, projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You are not a member of that project")
        val payload = request.toPayload()

        val board = access.boardOf(userId, projectId)
        val existing = boardCardRepository.findAllByBoardId(board.id)

        val now = Instant.now()
        val card = boardCardRepository.save(
            BoardCard(
                boardId = board.id,
                kind = request.kind,
                owner = BoardCardOwner.HIRE,
                position = (existing.maxOfOrNull { it.position } ?: -1) + 1,
                placedAt = now.takeIf { by == BoardActor.BUDDY },
                payload = json.encodeToString(payload),
            ).apply { recordChange(BoardCardChange.CREATED, by, now) },
        )
        return hydrator.render(card, member, projectId)
    }

    /**
     * Replaces what one of the hire's own cards says. The payload is written whole, not patched.
     *
     * Ticking a checklist item comes through here too, which is why items carry ids: a tick is an
     * edit to that line, not to a position.
     *
     * @throws ResponseStatusException 404 when the card is not one of theirs, 400 when the content
     * is empty or the kind does not match the card being edited.
     */
    fun editAuthoredCard(userId: UserId, cardId: BoardCardId, request: AuthoredCardRequest): BoardCardResponse {
        val (card, board) = editableCardOrThrow(userId, cardId, request.kind)

        val before = card.payload
        card.payload = json.encodeToString(request.toPayload())
        card.recordChange(changeBetween(before, card.payload), BoardActor.HIRE)
        boardCardRepository.save(card)

        val projectId = ProjectId(board.projectId)
        val member = access.memberOrNull(userId, projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You are not a member of that project")
        return hydrator.render(card, member, projectId)
    }

    /**
     * Adds lines to the end of one of the hire's checklists, and can do nothing else to it.
     *
     * **Append-only, enforced here rather than asked of the caller.** [editAuthoredCard] replaces a
     * card's content whole, which is right for the hire editing their own card and wrong for the
     * mentor adding to one: given the whole list to send back, a model that rewords a line it
     * dislikes, drops one it thinks is done, or reorders them into what it considers a better
     * sequence has silently edited the hire's card, and the hire has no way to see what changed.
     * So the existing items are read from storage and copied through untouched — their ids, their
     * words, their ticks — and the new lines can only land after them.
     *
     * Ids are minted here for the new lines, the same way [addAuthoredCard] mints them, so a tick
     * still lands on a line rather than on a position.
     *
     * @throws ResponseStatusException 404 when they are not a member of [projectId], or the card is
     * not an active checklist of theirs on that project's board; 400 when there is nothing to add.
     */
    fun appendChecklistItems(
        userId: UserId,
        projectId: ProjectId,
        cardId: BoardCardId,
        lines: List<String>,
    ): BoardCardResponse {
        val (card, member) = buddyEditableCardOrThrow(userId, projectId, cardId, BoardCardKind.CHECKLIST)
        val existing = card.checklistOrThrow()
        val added = lines.filter { it.isNotBlank() }.ifEmpty {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "There were no lines to add")
        }

        card.rewriteForBuddy(
            existing.copy(
                items = existing.items +
                    added.map { ChecklistItemPayload(id = UUID.randomUUID().toString(), text = it) },
            ),
        )

        return hydrator.render(card, member, projectId)
    }

    /**
     * Ticks lines the hire says they have done, and can do nothing else to the card.
     *
     * Matched by their **words**, not by an id, for the reason marks are (`marks/cardMarks.ts`):
     * making this work by id would mean putting every item's id in the mentor's prompt, and the
     * mentor would then be one slip away from reading one out. A line the text does not match is
     * simply not ticked, and the caller is told how many were — silence would let a typo look like
     * success.
     *
     * **It only ever sets done, never clears it.** Un-ticking is the hire saying they were wrong
     * about their own work, which is not something anybody should be able to do on their behalf;
     * the checkbox on the card is right there. Nothing else moves either: no text changes, no
     * re-ordering, no lines added or dropped.
     *
     * @throws ResponseStatusException 404 when they are not a member of [projectId], or the card is
     * not an active checklist of theirs on that project's board.
     */
    fun tickChecklistItems(userId: UserId, projectId: ProjectId, cardId: BoardCardId, lines: List<String>): Int {
        val (card, _) = buddyEditableCardOrThrow(userId, projectId, cardId, BoardCardKind.CHECKLIST)
        val existing = card.checklistOrThrow()
        val wanted = lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

        var ticked = 0
        val items = existing.items.map { item ->
            if (!item.done && item.text.trim().lowercase() in wanted) {
                ticked++
                item.copy(done = true)
            } else {
                item
            }
        }
        if (ticked == 0) return 0

        card.rewriteForBuddy(existing.copy(items = items), BoardCardChange.TICKED)

        return ticked
    }

    /**
     * Rewrites one line of a checklist, keeping everything the line is apart from its words.
     *
     * Its id survives, so a tick stays on the line rather than sliding to a neighbour, and so does
     * whether it was ticked — rewording a step is not undoing it. Nothing else on the card moves.
     *
     * **Refuses an ambiguous match rather than picking one.** Two lines that read the same are rare
     * and a card where the wrong one silently changed is worse than a card that did not change: the
     * hire asked for one edit and would have to diff the list to find out they got another.
     *
     * @return true when a line was rewritten, false when the text matched none or more than one.
     * @throws ResponseStatusException 404 when they are not a member of [projectId], or the card is
     * not an active checklist of theirs on that project's board.
     */
    fun rewordChecklistItem(hire: HireBoard, cardId: BoardCardId, before: String, after: String): Boolean {
        val (userId, projectId) = hire
        val (card, _) = buddyEditableCardOrThrow(userId, projectId, cardId, BoardCardKind.CHECKLIST)
        val existing = card.checklistOrThrow()
        val wanted = before.trim().lowercase()
        val words = after.trim()
        if (words.isEmpty()) return false

        val matches = existing.items.filter { it.text.trim().lowercase() == wanted }
        if (matches.size != 1) return false

        card.rewriteForBuddy(
            existing.copy(
                items = existing.items.map { item ->
                    if (item.id == matches.first().id) item.copy(text = words) else item
                },
            ),
        )

        return true
    }

    /**
     * Replaces what one of the hire's notes, links or checklists says, on the buddy's behalf, once
     * the hire has confirmed the new content.
     *
     * Written whole, like the hire's own [editAuthoredCard] — the confirm showed the whole card, so
     * the whole card is what lands. A checklist keeps what its lines *are* apart from their words:
     * a line whose text survives the edit keeps its id and its tick, so tidying a list does not
     * silently undo the work already ticked off it, and a tick still lands on a line rather than a
     * position. A line the edit drops is gone; a new one arrives unticked.
     *
     * @param basedOn The fingerprint ([BoardCardVersion]) of the card as the proposal read it.
     * @throws ResponseStatusException 404 when they are not a member of [projectId], or the card is
     * not an active card of that kind on that project's board; 400 when the content is empty; 409
     * when the card's words are not those [basedOn] names, or it is a note longer than the buddy can
     * read, so that replacing it whole would lose what the proposal never showed.
     */
    fun editAuthoredCardForBuddy(
        hire: HireBoard,
        cardId: BoardCardId,
        request: AuthoredCardRequest,
        basedOn: String?,
    ): BoardCardResponse {
        val (userId, projectId) = hire
        val (card, member) = buddyEditableCardOrThrow(userId, projectId, cardId, request.kind)
        // Decided on the row this transaction holds the lock on, so nothing can change it between
        // this check and the replace below. A missing fingerprint cannot vouch for anything.
        if (basedOn == null || basedOn != BoardCardVersion.of(card.decodedPayload())) {
            throw ResponseStatusException(HttpStatus.CONFLICT, BoardCardVersion.CARD_CHANGED)
        }
        if (request is NoteCardRequest && card.noteLength() > BoardReading.NOTE_PREVIEW) {
            throw ResponseStatusException(HttpStatus.CONFLICT, BoardCardVersion.NOTE_TOO_LONG_TO_REPLACE)
        }
        val content = if (request is ChecklistCardRequest) {
            request.keepingLinesOf(card.checklistOrThrow())
        } else {
            request
        }

        card.rewriteForBuddy(content.toPayload())

        return hydrator.render(card, member, projectId)
    }

    /**
     * The card this edit is allowed to change, with the board it sits on.
     *
     * Three refusals, all answering 404: a card that does not exist, one belonging to
     * somebody else, and a live card (which has no stored content to edit). A 403 would confirm the
     * id is somebody's real card.
     */
    private fun editableCardOrThrow(
        userId: UserId,
        cardId: BoardCardId,
        kind: BoardCardKind,
    ): Pair<BoardCard, Board> {
        // Locked, so a hire edit and a buddy edit of the same card cannot interleave their writes.
        val card = boardCardRepository.findLockedById(cardId.value)
        val board = card?.let { boardRepository.findById(it.boardId).orElse(null) }
        if (card == null || board == null || !isHiresOwn(card, board, userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such card on your board")
        }
        if (card.kind != kind) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "That card is a ${card.kind}, not a $kind")
        }
        return card to board
    }

    private fun isHiresOwn(card: BoardCard, board: Board, userId: UserId): Boolean =
        board.userId == userId.value && card.owner == BoardCardOwner.HIRE

    /**
     * The card a buddy edit may change, locked until the edit commits.
     *
     * **This is where the buddy is let into the hire's own cards, and the only place.** The buddy
     * may do to the hire's board what the hire can — create, edit, tick, dismiss, move — provided
     * every change is one the hire confirmed and every change is attributed to the buddy
     * ([BoardCard.recordChange]). Pre-approval and attribution are what make that safe; nothing
     * replaces them. So the access lives here rather than as a special case in each write: the
     * owner check below admits only [BoardCardOwner.HIRE] cards — a live card has no stored
     * content for anybody to edit — and every buddy write to a card's content comes through this.
     *
     * Stricter than [editableCardOrThrow] on purpose. The hire's own edit comes from a board they
     * are looking at; a buddy edit is confirmed from a proposal that may be stale — made on a
     * project they have since left, or before they took the card off their board. So it must be
     * a member of [projectId] **now**, on that project's board, and the card must still be an
     * active card of theirs, of the kind the proposal said.
     *
     * Membership is checked before anything is read, so a hire who has left the project is
     * refused before a write can happen, not after one already has. The card is read through the
     * lock, which is what makes the read-change-write in each caller atomic.
     *
     * One refusal for every way the card can be wrong, the same as [editableCardOrThrow]: whether
     * a card exists on somebody else's board is not this caller's business.
     *
     * @throws ResponseStatusException 404 in every case.
     */
    private fun buddyEditableCardOrThrow(
        userId: UserId,
        projectId: ProjectId,
        cardId: BoardCardId,
        kind: BoardCardKind,
    ): Pair<BoardCard, ProjectMember> {
        val member = access.memberOrNull(userId, projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You are not a member of that project")
        val hiresBoard = access.existingBoardOf(userId, projectId)
        val card = hiresBoard
            ?.let { board -> boardCardRepository.findLockedById(cardId.value)?.takeIf { it.boardId == board.id } }
            ?.takeIf { it.owner == BoardCardOwner.HIRE && it.state == BoardCardState.ACTIVE }
            ?.takeIf { it.kind == kind }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such ${kind.name.lowercase()} on your board")
        return card to member
    }

    private fun BoardCard.rewriteForBuddy(content: BoardCardPayload, change: BoardCardChange = BoardCardChange.EDITED) {
        payload = json.encodeToString(content)
        recordChange(change, BoardActor.BUDDY)
        boardCardRepository.save(this)
    }
}
