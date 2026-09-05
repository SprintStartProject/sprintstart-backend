package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardStage
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CardDependencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CardWidth
import com.sprintstart.sprintstartbackend.onboarding.external.enums.HighlightColor
import kotlinx.serialization.Serializable

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
    val cards: Map<String, CardStructurePayload> = emptyMap(),
    /** Named parts of the board, in the order they are shown. */
    val groups: List<BoardGroupPayload> = emptyList(),
    /**
     * The stage a whole area sits in, by group id.
     *
     * Beside [cards] rather than on [BoardGroupPayload], because it answers the same question a
     * card's own stage does and the two are read together — an area sequenced in one gesture and a
     * card sequenced on its own have to end up in the same band.
     */
    val groupStages: Map<String, BoardStage> = emptyMap(),
    /** Cards folded down to their header. */
    val collapsedCardIds: List<String> = emptyList(),
    /** Cards held at the top of the board. */
    val pinnedCardIds: List<String> = emptyList(),
    /** Widths the hire pulled cards to. A card at the default width is absent, not stored as one. */
    val sizes: Map<String, CardSizePayload> = emptyMap(),
    /** Where a card was found, for the ones that were found somewhere. */
    val origins: Map<String, CardOriginPayload> = emptyMap(),
    /**
     * Highlights, by card id.
     *
     * On a `NOTE` these carry colour only — *whether* something is marked is written into the note's
     * own text as `==like this==`, so that a highlight survives on a machine that has never seen
     * this record. On every other kind, whose text is read from the server and has nowhere to put a
     * delimiter, they carry both.
     */
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
    val markLabels: Map<HighlightColor, String> = emptyMap(),
)

/** What the hire has said about one card. Every field is optional: silence is the honest default. */
@Serializable
data class CardStructurePayload(
    val stage: BoardStage? = null,
    /** The cards this one comes after, each with who said so. */
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
    val id: String,
    val name: String,
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
    val url: String,
    val label: String,
)

/**
 * One highlight: the words, and what colour they were painted.
 *
 * The words rather than character offsets, and that is the whole design. Offsets are meaningless the
 * moment the card is edited, and the generated cards are re-read from the server on every visit —
 * an offset would leave a highlight over the middle of an unrelated word. A string either still
 * appears in the card or it does not, and when it does not, nothing lights up.
 */
@Serializable
data class CardMarkPayload(
    val text: String,
    val color: HighlightColor = HighlightColor.YELLOW,
)
