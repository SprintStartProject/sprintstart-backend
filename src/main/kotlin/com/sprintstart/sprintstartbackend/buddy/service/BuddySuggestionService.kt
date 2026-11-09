package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.assistant.external.HireTool
import com.sprintstart.sprintstartbackend.buddy.model.response.buddy.BuddySuggestionResponse
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
 * Order follows the spec list, so the onboarding path comes first.
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
    hireTools: List<HireTool>,
) {
    /** The chips the features' own tools bring, by tool name. */
    private val toolChips: Map<String, BuddySuggestionResponse> = hireTools
        .mapNotNull { tool ->
            tool.suggestion?.let { tool.spec.name to BuddySuggestionResponse(it.label, it.question) }
        }.toMap()

    /** The chips for the caller behind [authId], or none when the auth id resolves to no user. */
    fun forMe(authId: String): List<BuddySuggestionResponse> =
        userApi
            .getUserIdByAuthId(authId)
            .map { forHire(it) }
            .orElse(emptyList())

    /**
     * The chips this hire should see, in mounted order: each mounted tool's own chip, if it has one.
     *
     * A tool without a chip contributes nothing, so adding a tool never silently adds a chip — the
     * wording for a hire is a separate decision from what the mentor can read.
     */
    fun forHire(userId: UUID): List<BuddySuggestionResponse> =
        buddyToolExecutor.toolSpecs(userId).mapNotNull { toolChips[it.name] }
}
