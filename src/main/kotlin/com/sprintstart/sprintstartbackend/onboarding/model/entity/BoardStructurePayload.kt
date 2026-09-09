package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardStage
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CardDependencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CardWidth
import com.sprintstart.sprintstartbackend.onboarding.external.enums.HighlightColor
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable

/**
 * How much of an arrangement the server will take.
 *
 * The arrangement is the one document a client hands over whole, and until these existed nothing
 * bounded it: a board could be PUT with a hundred thousand pinned ids, stored as unbounded `TEXT`,
 * and then read back into every prompt the buddy builds for that board. The numbers are not a
 * judgement about how anybody works — they are far above a board a person could make by hand, and
 * low enough that one client cannot decide how large the next model call is.
 *
 * Enforced at the controller with [Valid], which is the cheap place: bounding the document once on
 * the way in is worth more than capping each place it is later read out.
 */
object BoardStructureLimits {
    /** Cards a hire may have said something about. A real board is tens of cards. */
    const val CARDS = 2_000

    /** Named areas, and the stages they sit in. */
    const val GROUPS = 500

    /** Ids in one list — pinned, folded, or the members of one area. */
    const val IDS = 2_000

    /**
     * Cards carrying at least one highlight.
     *
     * How many marks one card carries is bounded by [DOCUMENT_CHARS] rather than by a count of its
     * own: a per-element constraint inside a map of lists buys a second kind of limit to keep in
     * step, and the number that actually protects the stored row and the prompt is the one on the
     * whole document.
     */
    const val MARKED_CARDS = 2_000

    /** "Comes after" entries on one card. */
    const val DEPENDENCIES = 500

    /** A card id or the name of an area: short strings the client made up. */
    const val SHORT_TEXT = 500

    /** Highlighted words, and the path back to where a card came from. */
    const val LONG_TEXT = 4_000

    /**
     * The whole encoded document, in characters.
     *
     * The backstop the field limits cannot give on their own: the caps above multiply, and a
     * document that satisfies every one of them can still be enormous. Checked once, on the encoded
     * form, because that is the thing that actually gets stored.
     */
    const val DOCUMENT_CHARS = 512_000
}

/**
 * Everything the hire has said *about* their board, as opposed to what is on it.
 *
 * The cards are rows; this is the arrangement around them — which come first, what waits on what,
 * what has been ticked off by hand, what is grouped and what it is called, what is folded, pinned,
 * how wide, where it came from, and what has been marked in it.
 *
 * All of it lived in the browser's local storage until now, and that was defensible only while it
 * was decoration. It stopped being decoration the moment anything else needed to read it: a buddy
 * that is asked "what should I do next" cannot answer from a board it cannot see, a PM cannot be
 * shown where a hire is stuck, and a hire who opens the app on a second machine finds their board
 * unarranged and reasonably concludes the app forgot them.
 *
 * Persisted as JSON in one column, the same bargain [BoardCardPayload] makes and for the same
 * reasons: it is small, it is read and written whole, and nothing ever queries inside it. A table
 * per concept — `board_card_stages`, `board_groups`, `board_card_marks` — would be six joins bought
 * with nothing.
 *
 * **Card ids are strings and are not validated against the cards that exist.** The structure is the
 * hire's own bookkeeping, and an entry for a card that has since been dismissed is ordinary rather
 * than exceptional; refusing the whole write over one stale id would lose the other forty entries.
 * The client drops what it cannot resolve when it reads.
 */
@Serializable
data class BoardStructurePayload(
    /** What the hire has said about individual cards, by card id. */
    @field:Size(max = BoardStructureLimits.CARDS)
    @field:Valid
    val cards: Map<String, CardStructurePayload> = emptyMap(),
    /** Named parts of the board, in the order they are shown. */
    @field:Size(max = BoardStructureLimits.GROUPS)
    @field:Valid
    val groups: List<BoardGroupPayload> = emptyList(),
    /**
     * The stage a whole area sits in, by group id.
     *
     * Beside [cards] rather than on [BoardGroupPayload], because it answers the same question a
     * card's own stage does and the two are read together — an area sequenced in one gesture and a
     * card sequenced on its own have to end up in the same band.
     */
    @field:Size(max = BoardStructureLimits.GROUPS)
    val groupStages: Map<String, BoardStage> = emptyMap(),
    /** Cards folded down to their header. */
    @field:Size(max = BoardStructureLimits.IDS)
    val collapsedCardIds: List<String> = emptyList(),
    /** Cards held at the top of the board. */
    @field:Size(max = BoardStructureLimits.IDS)
    val pinnedCardIds: List<String> = emptyList(),
    /** Widths the hire pulled cards to. A card at the default width is absent, not stored as one. */
    @field:Size(max = BoardStructureLimits.CARDS)
    val sizes: Map<String, CardSizePayload> = emptyMap(),
    /** Where a card was found, for the ones that were found somewhere. */
    @field:Size(max = BoardStructureLimits.CARDS)
    @field:Valid
    val origins: Map<String, CardOriginPayload> = emptyMap(),
    /**
     * Highlights, by card id.
     *
     * On a `NOTE` these carry colour only — *whether* something is marked is written into the note's
     * own text as `==like this==`, so that a highlight survives on a machine that has never seen
     * this record. On every other kind, whose text is read from the server and has nowhere to put a
     * delimiter, they carry both.
     */
    @field:Size(max = BoardStructureLimits.MARKED_CARDS)
    val marks: Map<String, List<CardMarkPayload>> = emptyMap(),
    /**
     * What this hire calls each highlight colour.
     *
     * The colours mean nothing on their own — "colour is never the message" is a rule the client
     * holds everywhere, and a sentence marked green does not mean it went well. A hire sorting a
     * board does mean something by them, though, and after a week they have forgotten which. So the
     * meaning is theirs to write down, which is the only arrangement that keeps both: nothing here
     * assigns a meaning, and the board can still show one back.
     *
     * A legend that did not follow the hire to another machine would be a legend for somebody
     * else's board, which is why it is part of the arrangement rather than a browser preference.
     */
    @field:Size(max = BoardStructureLimits.GROUPS)
    val markLabels: Map<HighlightColor, String> = emptyMap(),
)

/** What the hire has said about one card. Every field is optional: silence is the honest default. */
@Serializable
data class CardStructurePayload(
    val stage: BoardStage? = null,
    /** The cards this one comes after, each with who said so. */
    @field:Size(max = BoardStructureLimits.DEPENDENCIES)
    @field:Valid
    val dependsOn: List<CardDependencyPayload> = emptyList(),
    /**
     * Ticked off by hand, for the kinds whose completion nothing can observe.
     *
     * A note has no notion of done and nothing watches a link, so this is the hire saying it. It is
     * never written for a card whose state is derived — that would be two answers to one question.
     */
    val markedDone: Boolean = false,
)

/** One "comes after", and who claimed it. See [CardDependencySource]. */
@Serializable
data class CardDependencyPayload(
    @field:Size(max = BoardStructureLimits.SHORT_TEXT)
    val id: String,
    val source: CardDependencySource = CardDependencySource.HIRE,
)

/**
 * A named part of the board.
 *
 * Survives being emptied. An area used to be dropped when its last card left, which was right while
 * the only way to make one was to put a card in it — and wrong as soon as one could be made empty
 * and named first. A hire who drags the last card out of *Paperwork* to look at it elsewhere should
 * not come back to find the area gone and its name with it.
 */
@Serializable
data class BoardGroupPayload(
    @field:Size(max = BoardStructureLimits.SHORT_TEXT)
    val id: String,
    @field:Size(max = BoardStructureLimits.SHORT_TEXT)
    val name: String,
    @field:Size(max = BoardStructureLimits.IDS)
    val cardIds: List<String> = emptyList(),
    val collapsed: Boolean = false,
)

@Serializable
data class CardSizePayload(
    val width: CardWidth = CardWidth.NORMAL,
)

/**
 * Where a card came from, and the way back to it.
 *
 * [url] is an in-app path, usually with a `#:~:text=` fragment naming the words that were selected,
 * so the card can return the reader to the paragraph rather than to the top of the page. Relative on
 * purpose: an absolute URL recorded on one machine names a host the next one does not have.
 */
@Serializable
data class CardOriginPayload(
    @field:Size(max = BoardStructureLimits.LONG_TEXT)
    val url: String,
    @field:Size(max = BoardStructureLimits.SHORT_TEXT)
    val label: String,
)

/**
 * One highlight: the words, and what colour they were painted.
 *
 * The words rather than character offsets, and that is the whole design. Offsets are meaningless the
 * moment the card is edited, and the generated cards are re-read from the server on every visit —
 * an offset would leave a highlight over the middle of an unrelated word. A string either still
 * appears in the card or it does not, and when it does not, nothing lights up.
 *
 * [text] defaults to empty because a mark on a `NOTE` carries colour only — the words are written
 * into the note's own text as `==like this==`, and there is nothing to repeat here. Making it
 * required would have left a client two ways to say the same thing: fail the whole arrangement over
 * one note-mark, or send `""` and have the buddy quote the empty string back as highlighted words.
 * Nothing reads a blank one: `BuddyBoardTools` leaves it out.
 */
@Serializable
data class CardMarkPayload(
    @field:Size(max = BoardStructureLimits.LONG_TEXT)
    val text: String = "",
    val color: HighlightColor = HighlightColor.YELLOW,
)
