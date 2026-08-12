package com.sprintstart.sprintstartbackend.onboarding.model.response.board

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.onboarding.model.response.arrival.ArrivalStepResponse
import java.time.Instant
import java.util.UUID

/**
 * A hire's board on one project: the cards on it, and the words to describe their work in.
 *
 * [vocabulary] sits on the board, not on each card: it is the same for every card.
 */
data class BoardResponse(
    val boardId: UUID,
    val projectId: UUID,
    val vocabulary: BoardVocabularyResponse,
    /** Active cards only, in board order. A dismissed card is gone from the hire's point of view. */
    val cards: List<BoardCardResponse>,
)

/**
 * How this hire's accepted work is named, taken from their track.
 *
 * The client builds sentences around live numbers ("2 changes merged") from these fields.
 * ⚠️ **Structured fields, never prose** — a track fills fixed slots, it does not write the sentence.
 */
data class BoardVocabularyResponse(
    /** The track's own name, e.g. "Engineering" — for saying whose board this is set up as. */
    val trackLabel: String,
    /** One unit of accepted work, bare: "change", "ceremony". */
    val contributionNoun: String,
    val contributionNounPlural: String,
    /** The hire's own act in the past tense: "merged", "facilitated". */
    val contributionVerbPast: String,
)

/**
 * One card, with the content it renders.
 *
 * [content] is polymorphic on [BoardCardKind], not a bag of optional fields. The catalog is closed,
 * so the union is complete by construction.
 */
data class BoardCardResponse(
    val id: UUID,
    val kind: BoardCardKind,
    val owner: BoardCardOwner,
    val position: Int,
    /**
     * When the mentor put this card here; null when the board keeps it as part of the baseline.
     *
     * ⚠️ The client says "your buddy added this" only for a card that has one.
     */
    val placedAt: Instant?,
    val content: BoardCardContent,
)

/**
 * The rendered content of one card.
 *
 * Every implementation is a live read composed at request time. None of them carry a copy of
 * anything stored on the card row, which is what guarantees a card and the buddy tool behind it
 * cannot disagree.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "kind",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(
        value = PathToFirstContributionContent::class,
        name = "PATH_TO_FIRST_CONTRIBUTION",
    ),
    JsonSubTypes.Type(value = ArrivalStepsContent::class, name = "ARRIVAL_STEPS"),
    JsonSubTypes.Type(value = OpenPullRequestsContent::class, name = "OPEN_PULL_REQUESTS"),
    JsonSubTypes.Type(value = CurrentTaskContent::class, name = "CURRENT_TASK"),
    JsonSubTypes.Type(value = SuggestedTasksContent::class, name = "SUGGESTED_TASKS"),
    JsonSubTypes.Type(value = CompetencyProgressContent::class, name = "COMPETENCY_PROGRESS"),
    JsonSubTypes.Type(value = MemoryRecapContent::class, name = "MEMORY_RECAP"),
    JsonSubTypes.Type(value = DiagramContent::class, name = "DIAGRAM"),
    JsonSubTypes.Type(value = NoteContent::class, name = "NOTE"),
    JsonSubTypes.Type(value = LinkContent::class, name = "LINK"),
    JsonSubTypes.Type(value = ChecklistContent::class, name = "CHECKLIST"),
)
sealed interface BoardCardContent {
    val kind: BoardCardKind
}

/**
 * The moments between joining and a first accepted piece of work.
 *
 * Composed from the hire's contribution timeline, so it holds for every track.
 * ⚠️ Every timestamp is nullable: "has not happened yet" is the normal state mid-onboarding, and it
 * is not the same as zero.
 */
data class PathToFirstContributionContent(
    override val kind: BoardCardKind = BoardCardKind.PATH_TO_FIRST_CONTRIBUTION,
    val moments: List<BoardMomentResponse>,
    /** How much accepted work there is so far — the ramp's only real counter. */
    val acceptedCount: Int,
    /** When onboarding ended for this hire, dated. Null while it is still going. */
    val autonomyReachedAt: Instant?,
    /** Why this hire currently reads as stalled, in plain words, or null when they do not. */
    val stalledReason: String?,
) : BoardCardContent

/**
 * What still has to be true before this hire can work, and what they have already settled.
 *
 * ⚠️ **Counts are per rigor and there is no total to divide by — never add a completion
 * percentage.** One would count a ticked box exactly like a passed check.
 */
data class ArrivalStepsContent(
    override val kind: BoardCardKind = BoardCardKind.ARRIVAL_STEPS,
    val steps: List<ArrivalStepResponse>,
    val observedCount: Int,
    val declaredCount: Int,
    val outstandingCount: Int,
) : BoardCardContent

/**
 * One moment on the path, and whether it has happened.
 *
 * [key] is a stable identifier the client maps to its own copy. ⚠️ [reachedAt] null means not yet;
 * the client renders it as a dash, not a zero.
 */
data class BoardMomentResponse(
    val key: BoardMomentKey,
    val reachedAt: Instant?,
)

/** The moments a path card reports, in the order they normally happen. */
enum class BoardMomentKey {
    JOINED,
    TASK_CLAIMED,
    WORK_SUBMITTED,
    FIRST_RESPONSE,
    WORK_ACCEPTED,
}

/**
 * The hire's still-open pull requests, longest-waiting first.
 *
 * Only present on a board whose track admits pull requests — see [BoardCardKind.OPEN_PULL_REQUESTS].
 */
data class OpenPullRequestsContent(
    override val kind: BoardCardKind = BoardCardKind.OPEN_PULL_REQUESTS,
    val pullRequests: List<BoardPullRequestResponse>,
    /**
     * True when the hire has declared no GitHub login, so nothing can be attributed to them.
     *
     * ⚠️ **Distinct from an empty list**: "nothing open" and "cannot tell" are different states.
     */
    val attributionMissing: Boolean,
) : BoardCardContent

/**
 * One open pull request.
 *
 * ⚠️ [waitingHours] is null once somebody has responded: the wait it measures has ended.
 */
data class BoardPullRequestResponse(
    val artifactId: UUID,
    val number: Int?,
    val title: String?,
    val url: String?,
    val waitingHours: Long?,
)

/**
 * The task the hire is on, or the fact that they are on none.
 *
 * ⚠️ **Present-but-empty when there is no task, never absent** — a card that vanishes reads as the
 * board losing something.
 */
data class CurrentTaskContent(
    override val kind: BoardCardKind = BoardCardKind.CURRENT_TASK,
    val taskId: UUID?,
    val title: String?,
    val summary: String?,
    val url: String?,
    /** True when the hire claimed this as their goal, false for a Task 0 they were handed. */
    val chosen: Boolean,
) : BoardCardContent

/**
 * Good next tasks, ranked by fit.
 *
 * ⚠️ Carries [BoardSuggestedTaskResponse.reasons] and **no score**.
 */
data class SuggestedTasksContent(
    override val kind: BoardCardKind = BoardCardKind.SUGGESTED_TASKS,
    val tasks: List<BoardSuggestedTaskResponse>,
) : BoardCardContent

/** One suggested task, with the plain reasons it was suggested and the id to claim it by. */
data class BoardSuggestedTaskResponse(
    val taskId: UUID,
    val title: String,
    val url: String?,
    val reasons: List<String>,
)

/** Something the hire wrote down. The one card whose text the board did not read from anywhere. */
data class NoteContent(
    override val kind: BoardCardKind = BoardCardKind.NOTE,
    val text: String,
) : BoardCardContent

/** A link the hire kept. A null [label] means render the URL itself. */
data class LinkContent(
    override val kind: BoardCardKind = BoardCardKind.LINK,
    val url: String,
    val label: String?,
) : BoardCardContent

/** A list the hire ticks off. */
data class ChecklistContent(
    override val kind: BoardCardKind = BoardCardKind.CHECKLIST,
    val title: String?,
    val items: List<ChecklistItemResponse>,
) : BoardCardContent

/** One checklist item. ⚠️ Identified by [id] so a tick edits the line, not a position. */
data class ChecklistItemResponse(
    val id: UUID,
    val text: String,
    val done: Boolean,
)

/**
 * What the hire has shown they can do, and what they are short of. Two lists, no percentage.
 *
 * ⚠️ **Level-0 rows are excluded**: they mean "asked, saw no evidence", not a held competency.
 */
data class CompetencyProgressContent(
    override val kind: BoardCardKind = BoardCardKind.COMPETENCY_PROGRESS,
    /** Meets its target level: shown, not merely started. */
    val held: List<BoardCompetencyResponse>,
    /** Progress made, target not yet met. */
    val inProgress: List<BoardCompetencyResponse>,
) : BoardCardContent

/** One competency, with the target level it is measured against. ⚠️ Never a score. */
data class BoardCompetencyResponse(
    val competencyKey: String,
    val label: String,
    val level: Int,
    val targetLevel: Int,
)

/**
 * A picture of how some part of this project fits together.
 *
 * ⚠️ **The model chooses the question, never the answer.** [subject] comes from the mentor;
 * everything else is derived from the project's material, one citation per box, with ungrounded
 * boxes dropped before returning.
 *
 * ⚠️ **A board read serves the last picture drawn, never a fresh one** — this card hydrates on
 * every page load and assembling costs a model call. The client revalidates through the diagram
 * endpoint, which checks the cache against the current corpus.
 *
 * [nodes] empty with a [reason] is an ordinary state.
 */
data class DiagramContent(
    override val kind: BoardCardKind = BoardCardKind.DIAGRAM,
    /** The question this diagram answers, as the mentor asked it. */
    val subject: String,
    val summary: String?,
    val nodes: List<BoardDiagramNodeResponse>,
    val edges: List<BoardDiagramEdgeResponse>,
    /** The material the picture drew on, so "this is wrong" has somewhere to point. */
    val sources: List<BoardDiagramSourceResponse>,
    /** When this picture was drawn; null when it never has been. */
    val assembledAt: Instant?,
    /** Why there is no picture, when there is none. Null whenever [nodes] is non-empty. */
    val reason: String?,
) : BoardCardContent

/**
 * One box.
 *
 * ⚠️ [citations] is never empty: an ungrounded node is dropped upstream, not shown unsourced.
 */
data class BoardDiagramNodeResponse(
    val id: String,
    val label: String,
    /** What this is — COMPONENT, FILE, SERVICE, DATA, STEP, EXTERNAL, or OTHER when unsettled. */
    val kind: String,
    val summary: String?,
    val citations: List<BoardDiagramCitationResponse>,
)

/** One arrow. Both ends name a box in the same diagram. */
data class BoardDiagramEdgeResponse(
    val fromId: String,
    val toId: String,
    /** FLOWS_TO, DEPENDS_ON, CONTAINS, or RELATES_TO when the evidence does not settle it. */
    val kind: String,
    val label: String?,
)

/** Where a box came from. A source with no URL is still named — unopenable beats unattributed. */
data class BoardDiagramCitationResponse(
    val filename: String,
    val sourceUrl: String?,
)

data class BoardDiagramSourceResponse(
    val filename: String,
    val sourceUrl: String?,
    val artifactType: String?,
)

/**
 * What the mentor remembers about this hire, in the mentor's own words.
 *
 * ⚠️ **The one card whose content a model wrote, and it is labelled as such** rather than
 * presented as fact.
 *
 * [memory] is null before the first visit has been folded, which is a real state.
 */
data class MemoryRecapContent(
    override val kind: BoardCardKind = BoardCardKind.MEMORY_RECAP,
    val memory: String?,
    /** How many messages the memory covers. */
    val messagesRemembered: Int,
) : BoardCardContent
