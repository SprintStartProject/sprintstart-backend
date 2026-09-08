package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddySuggestionResponse
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * What this hire could usefully ask their buddy, for the chips beside the composer.
 *
 * The buddy's most useful capabilities were reachable only by knowing what to type, which is the
 * tutor's sharpest note: *"wenn der User einen Befehl nicht weiß oder nicht mal weiß, dass es
 * überhaupt über den Chat geht, dann wird es kaum verwendet werden."* A chip answers the second
 * half of that — it is a person looking at an empty composer with no idea what goes in it.
 *
 * Derived from what is mounted, never listed independently. Every chip names a tool and is
 * offered only when that tool is mounted for this hire — the same per-hire gate
 * [BuddyToolExecutor.toolSpecs] applies. Deriving rather than listing is what makes the two
 * incapable of disagreeing, and a chip is louder than a tool because the hire sees it.
 *
 * Order follows the spec list, so arrival comes first.
 *
 * No action tools. `claim_goal`, `request_attestation` and the rest are proposed by the
 * mentor and confirmed by the hire; a chip naming one would read as a button that does it. A chip
 * asks a question.
 *
 * No `search_canonical_answers`, no `place_card` — the first needs a query only the hire can
 * supply, and the second is something the mentor does *to* the board.
 */
@Service
class BuddySuggestionService(
    private val buddyToolExecutor: BuddyToolExecutor,
    private val userApi: UserApi,
) {
    /** The chips for the caller behind [authId], or none when the auth id resolves to no user. */
    fun forMe(authId: String): List<BuddySuggestionResponse> =
        userApi
            .getUserIdByAuthId(authId)
            .map { forHire(it) }
            .orElse(emptyList())

    /**
     * The chips this hire should see, in mounted order.
     *
     * A tool with no entry in the catalog contributes nothing, so adding a tool never silently adds
     * a chip — the wording for a hire is a separate decision from what the mentor can read.
     */
    fun forHire(userId: UUID): List<BuddySuggestionResponse> =
        buddyToolExecutor.toolSpecs(userId).mapNotNull { CATALOG[it.name] }

    private companion object {
        /**
         * Tool name → the chip offered for it.
         *
         * Wording may only assume what the chip's own gate guarantees. A chip is offered exactly
         * when its tool is mounted, so a chip whose tool mounts for everybody must be true of a
         * designer, a Scrum Master and a developer alike — no "clone", "repository", "commit" or
         * "pull request" in it. `BuddySuggestionServiceTest` greps for exactly that vocabulary.
         *
         * The label is what the chip says; the question is what lands in the composer. They differ
         * only in length — a chip is a doorway, and one that promised something other than the
         * question it writes would be putting words in the hire's mouth.
         */
        val CATALOG: Map<String, BuddySuggestionResponse> = mapOf(
            BuddyToolExecutor.GET_ARRIVAL_STEPS to BuddySuggestionResponse(
                label = "What do I still need?",
                question = "What do I still need to get set up?",
            ),
            BuddyToolExecutor.GET_SUGGESTED_TASKS to BuddySuggestionResponse(
                label = "What should I work on?",
                question = "What should I work on next?",
            ),
            BuddyToolExecutor.GET_MY_OPEN_PULL_REQUESTS to BuddySuggestionResponse(
                label = "Is my PR stuck?",
                question = "Are any of my pull requests stuck waiting on a review?",
            ),
            BuddyToolExecutor.GET_MY_METRICS to BuddySuggestionResponse(
                label = "How am I doing?",
                question = "How is my onboarding going so far?",
            ),
            BuddyToolExecutor.GET_MY_COMPETENCIES to BuddySuggestionResponse(
                label = "Where do I stand?",
                question = "Where do I stand — what have I shown so far?",
            ),
            // Mounted with attestation, so it is only offered where somebody could actually be
            // asked. The chip stops at naming people: whether that ends in request_attestation is
            // the mentor's call and the hire's confirmation, not the chip's.
            BuddyToolExecutor.GET_TEAMMATES to BuddySuggestionResponse(
                label = "Who's on my team?",
                question = "Who else is on my project?",
            ),
        )
    }
}
