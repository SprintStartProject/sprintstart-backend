package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * The bounded catalog of cards a board can hold.
 *
 * A closed catalog, never free-form content. A card the buddy places is a request to show a
 * *known* read, never a request to render prose it wrote about the hire's state: the mentor decides
 * what to show, the backend decides what it says. Cards the *hire* writes are the exception —
 * that content is prose, it is theirs, and the mentor cannot touch it.
 *
 * A live card stores nothing but its own existence; the content is re-read on every board
 * load from the same services the buddy's tools use, so a card and the tool of the same name cannot
 * disagree. An authored card is frozen when it was written and stores its content in the row.
 */
enum class BoardCardKind(
    val placement: Placement,
) {
    /**
     * The moments between joining and a first accepted piece of work, and which have happened.
     *
     * Composed from contributions, not pull requests, so the wording the board carries is what
     * names one unit of accepted work.
     */
    PATH_TO_FIRST_CONTRIBUTION(Placement.BASELINE),

    /**
     * What still has to be true before this hire can work: accounts, access, a machine that builds.
     *
     * Baseline rather than mentor-placed for the same reason the path card is: nobody should depend
     * on a model noticing that somebody has been unable to clone the repository for a week. It is
     * ensured on every board read and is the one card that is *most* useful on day one, when the
     * board is otherwise thin.
     *
     * It shows outstanding work; it does not withhold anything. An unsettled step never
     * stops a hire claiming a task, and nothing anywhere consults these rows before serving them.
     * The card exists so somebody blocked by their employer can see what they are waiting on — not
     * so the tool can wait on it too.
     *
     * Ensured only once at least one step actually applies to the hire, which keeps it out of the
     * way on an installation where nobody has authored any: a permanently empty card is the same
     * "absent, never empty" rule [OPEN_PULL_REQUESTS] follows.
     */
    ARRIVAL_STEPS(Placement.BASELINE),

    /**
     * The hire's still-open pull requests, named, with how long each has waited.
     *
     * Genuinely pull-request-shaped rather than generically about work in flight: it lists numbers,
     * titles and links, and a [Contribution] deliberately carries none of those — it is a
     * measurement surface, not a record of artifacts. So this card is mounted exactly where the
     * buddy's `get_my_open_pull_requests` tool is mounted, and is simply absent otherwise. An empty
     * "your open pull requests" card in front of somebody who will never have one is the
     * invisible-hire problem in card form.
     */
    OPEN_PULL_REQUESTS(Placement.BASELINE),

    /**
     * The task the hire is on, and where it came from.
     *
     * Not part of the baseline, because it is only true some of the time — somebody with no claimed
     * goal and no Task 0 is not "between tasks", they simply have no task, and a card about nothing
     * is worse than no card. The mentor places it, and confirming `claim_goal` places it too.
     */
    CURRENT_TASK(Placement.MENTOR),

    /**
     * Good next tasks for the hire, ranked, each with the plain reason it was suggested.
     *
     * The other half of the pair: worth pinning when somebody is looking for work, pointless when
     * they already have some. Which of those is true is exactly the kind of thing the mentor knows
     * from the conversation and the board does not.
     */
    SUGGESTED_TASKS(Placement.MENTOR),

    /**
     * What the hire has shown they can do, and what they are still short of.
     *
     * Placed rather than baseline because it is only worth looking at once there is something on
     * the ledger — an empty "competencies held: 0" on day one tells somebody they have achieved
     * nothing, which is both true and useless.
     */
    COMPETENCY_PROGRESS(Placement.MENTOR),

    /**
     * What the mentor remembers about this hire, in its own words.
     *
     * The buddy's memory is what carries continuity across visits, since the transcript is not
     * replayed. This card is that memory made visible: without it a hire cannot see what their
     * mentor thinks it knows about them, let alone correct it. It is the one card whose content
     * the model wrote — and it is labelled as the mentor's own words rather than presented as a
     * fact about the hire, because that is what it is.
     */
    MEMORY_RECAP(Placement.MENTOR),

    /**
     * A picture of how some part of this project fits together, drawn from the project's material.
     *
     * The one card that needs the mentor to supply something beyond its kind: a diagram is *of*
     * something, and only the conversation knows whether the mentor just explained authentication or
     * the ingestion pipeline. So `place_card` carries a subject for this kind — and the rule it
     * bends is bent in exactly one direction, worth naming rather than leaving implied:
     *
     * > The model may choose the question. It never writes the answer.
     *
     * The subject aims retrieval and is asserted nowhere. Every node comes back derived from the
     * corpus with the citation proving it, and a node that cannot be grounded is dropped — so a
     * subject the model invented cannot become a claim the model invented. Live, not authored: the
     * row stores the question, never the picture, so a diagram cannot describe code that has moved.
     *
     * The only non-authored kind a board may hold several of, because two subjects are two different
     * diagrams — and re-placing an existing one would let the mentor repurpose a picture the hire
     * chose to keep.
     */
    DIAGRAM(Placement.MENTOR),

    /** Something the hire wrote down. Markdown, theirs, and nothing reads it back as fact. */
    NOTE(Placement.AUTHORED),

    /** A link the hire wants to keep. The smallest possible card, and probably the most used. */
    LINK(Placement.AUTHORED),

    /** A list the hire ticks off. The only card whose content changes by being *used*. */
    CHECKLIST(Placement.AUTHORED),
    ;

    /**
     * How a card of this kind gets onto a board.
     *
     * The distinction is between *what a hire always needs*, *what the mentor decided was worth
     * keeping*, and *what the hire put there themselves* — and it decides everything downstream:
     * which kinds are ensured automatically, which the buddy may place, which may appear more than
     * once, and who may edit one.
     */
    enum class Placement {
        /**
         * Ensured on every board read, without anybody deciding.
         *
         * Deterministic on purpose: nobody should depend on a language model noticing that their
         * pull request has been waiting a week.
         */
        BASELINE,

        /**
         * Placed by the mentor, in conversation.
         *
         * The mentor chooses *that* the card belongs there; its content is still a live read, so it
         * never chooses what the card says. [DIAGRAM] is the one kind that also takes a subject
         * — the question, never the answer — for the reason given on it.
         */
        MENTOR,

        /**
         * Written by the hire.
         *
         * The only cards that carry stored content, the only ones a board may hold several of, and
         * the only ones the mentor must never touch. A board the mentor can tidy is a board the
         * hire cannot trust to keep what they put on it.
         */
        AUTHORED,
    }
}
