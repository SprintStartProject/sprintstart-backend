package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyActionResponse
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.HireAction
import com.sprintstart.sprintstartbackend.assistant.external.HireActionContext
import com.sprintstart.sprintstartbackend.assistant.external.HireActionOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireActionOutcome
import com.sprintstart.sprintstartbackend.assistant.external.HireActionProposal
import com.sprintstart.sprintstartbackend.assistant.external.HireActionRequest
import com.sprintstart.sprintstartbackend.assistant.external.HireActionScope
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/**
 * Offers to record the GitHub account the hire named, and records it through the one writer that owns it.
 *
 * Not project-scoped: a GitHub login is a fact about a *person* — the same reason `GET /me/arrival` is not
 * project-scoped — and a hire on no project yet is exactly the hire most likely to be setting one. It stays
 * in the Buddy rather than in `user` because `user` sits below the assistant kernel.
 *
 * ### Why this action exists
 *
 * A hire testing the buddy said *"my GitHub username is …"* and nothing was recorded, because no tool could.
 * The model went looking through the corpus for how to store one. Meanwhile the login is what artifact
 * verification compares a pull request's author against, so the failure is not cosmetic: work they really do
 * goes on not being credited, and they read as calm rather than blocked.
 *
 * [com.sprintstart.sprintstartbackend.user.service.GithubLoginService] stays the single writer — the
 * conversation is a second *entry point*, never a second source of truth. That is also what keeps the
 * normalisation, the uniqueness check and the rule that a changed login discards the old verification
 * verdict true here without restating any of them.
 */
@Component
@Order(HireActionOrder.SET_GITHUB_LOGIN)
class SetGithubLoginAction(
    private val userApi: UserApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PERSON
    override val gerund = "save a GitHub username"

    /**
     * Only the *shape* is checked here — that a username was actually supplied. Whether it is a possible
     * GitHub name, and whether somebody else already claims it, stay with `GithubLoginService` at confirm
     * time: it owns those rules for every other writer, and a second opinion here would be one more place
     * for them to drift.
     */
    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val login = call.textArgument("login")
        if (login.isBlank()) {
            return HireActionOutcome(
                "No username was provided. Ask the hire for their GitHub username before offering " +
                    "to save it.",
                null,
            )
        }
        return HireActionOutcome(
            toolResult = "Proposed to the hire: save “$login” as their GitHub username. They will " +
                "see a confirm button; it is saved only if they click it. Offer it — do not claim " +
                "it is done.",
            proposal = HireActionProposal(
                action = SET_GITHUB_LOGIN,
                label = LABEL,
                question = null,
                githubLogin = login,
            ),
        )
    }

    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val login = request.githubLogin
        if (login.isNullOrBlank()) {
            return BuddyActionResponse(ok = false, message = "No username was proposed to save.")
        }
        return try {
            val saved = userApi.setGithubLogin(context.hire.value, login)
            BuddyActionResponse(
                ok = true,
                message = "Saved — your work will be attributed to @$saved from now on. If that is not " +
                    "right, you can change it in Settings any time.",
            )
        } catch (ex: ResponseStatusException) {
            // 400 for a syntactically impossible username, 409 when another hire already claims it. Both
            // are things the hire can act on, so they come back as the sentence GithubLoginService wrote.
            BuddyActionResponse(ok = false, message = ex.reason ?: "That username could not be saved.")
        }
    }

    companion object {
        const val SET_GITHUB_LOGIN = "set_github_login"
        const val LABEL = "Save this username"

        val SPEC = BuddyToolSpecDto(
            name = SET_GITHUB_LOGIN,
            description = "Offer to save the hire's GitHub username. Use this the moment they tell " +
                "you one -- 'my GitHub is octocat', 'I'm @octocat on GitHub' -- rather than sending " +
                "them to Settings. It matters: this is the account their pull requests are matched " +
                "against, so until it is set, work they really do is not credited to them and " +
                "nothing says so. Pass exactly what they gave you, without the @. Proposing does " +
                "not save it; they confirm.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("login") {
                        put("type", "string")
                        put("description", "The GitHub username the hire gave, without a leading @.")
                    }
                }
                putJsonArray("required") { add("login") }
            },
        )
    }
}
