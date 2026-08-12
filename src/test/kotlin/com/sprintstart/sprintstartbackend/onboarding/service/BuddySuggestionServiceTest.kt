package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class BuddySuggestionServiceTest {
    private val buddyToolExecutor: BuddyToolExecutor = mockk()
    private val userApi: UserApi = mockk()

    private val service = BuddySuggestionService(buddyToolExecutor, userApi)

    private val userId = UUID.randomUUID()
    private val authId = "auth-1"

    private fun spec(name: String) = BuddyToolSpecDto(name = name, description = "", parameters = buildJsonObject { })

    private fun mounted(vararg names: String) {
        every { buddyToolExecutor.toolSpecs(userId) } returns names.map(::spec)
    }

    @Test
    fun `offers a chip for each mounted tool it has wording for`() {
        mounted(
            BuddyToolExecutor.GET_MY_METRICS,
            BuddyToolExecutor.GET_SUGGESTED_TASKS,
        )

        assertThat(service.forHire(userId).map { it.label })
            .containsExactly("How am I doing?", "What should I work on?")
    }

    /**
     * ⚠️ The whole point of deriving rather than listing. `get_my_open_pull_requests` is mounted
     * only for a track that admits pull requests, so the chip cannot be offered to a Scrum Master —
     * and unlike the tool, a chip is something the hire *sees*, so getting this wrong is louder.
     */
    @Test
    fun `does not offer the pull-request chip when the tool is not mounted`() {
        mounted(
            BuddyToolExecutor.GET_MY_METRICS,
            BuddyToolExecutor.GET_MY_COMPETENCIES,
            BuddyToolExecutor.GET_SUGGESTED_TASKS,
        )

        assertThat(service.forHire(userId).map { it.label }).doesNotContain("Is my PR stuck?")
    }

    @Test
    fun `offers the pull-request chip when the tool is mounted`() {
        mounted(BuddyToolExecutor.GET_MY_OPEN_PULL_REQUESTS)

        assertThat(service.forHire(userId))
            .singleElement()
            .satisfies({ assertThat(it.question).contains("pull request") })
    }

    /**
     * ⚠️ A fixed chip is out of reach of the track vocabulary, so every chip a non-developer can
     * see must be true of a designer and a Scrum Master too. Only the chip whose own gate is the
     * pull-request evidence kind may speak that language.
     */
    @Test
    fun `no chip a non-developer sees mentions developer work`() {
        mounted(
            BuddyToolExecutor.GET_ARRIVAL_STEPS,
            BuddyToolExecutor.GET_MY_METRICS,
            BuddyToolExecutor.GET_MY_COMPETENCIES,
            BuddyToolExecutor.GET_SUGGESTED_TASKS,
            BuddyToolExecutor.GET_TEAMMATES,
            BuddyPlanTools.GET_LEARNING_PLAN,
        )

        val words = listOf("pull request", "pr ", "merge", "commit", "clone", "repository", "branch", "code")
        val offered = service.forHire(userId)
        assertThat(offered).isNotEmpty()
        offered.forEach { suggestion ->
            val text = (suggestion.label + " " + suggestion.question).lowercase()
            words.forEach { word ->
                assertThat(text).doesNotContain(word)
            }
        }
    }

    /**
     * Arrival is first in the spec list because what has to be true before somebody can work comes
     * before how their work is going. Chips inherit that ordering rather than having an opinion of
     * their own — two orderings would eventually disagree.
     */
    @Test
    fun `keeps the order the tools are mounted in`() {
        mounted(
            BuddyToolExecutor.GET_ARRIVAL_STEPS,
            BuddyToolExecutor.GET_MY_METRICS,
            BuddyToolExecutor.GET_SUGGESTED_TASKS,
        )

        assertThat(service.forHire(userId).map { it.label })
            .containsExactly("What do I still need?", "How am I doing?", "What should I work on?")
    }

    /**
     * The catalog is opt-in: a tool with no wording contributes nothing. Adding a tool must not
     * silently add a chip — what the mentor may read and what a hire is invited to ask are separate
     * decisions, and only one of them is visible to the hire.
     */
    @Test
    fun `ignores mounted tools with no chip of their own`() {
        mounted(BuddyToolExecutor.SEARCH_CANONICAL_ANSWERS, "place_card")

        assertThat(service.forHire(userId)).isEmpty()
    }

    @Test
    fun `resolves the caller before reading their tools`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        mounted(BuddyToolExecutor.GET_SUGGESTED_TASKS)

        assertThat(service.forMe(authId).map { it.label }).containsExactly("What should I work on?")
    }

    @Test
    fun `offers nothing when the caller resolves to no user`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

        assertThat(service.forMe(authId)).isEmpty()
    }
}
