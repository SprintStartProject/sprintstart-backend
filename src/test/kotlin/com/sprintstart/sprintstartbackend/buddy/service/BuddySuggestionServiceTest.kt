package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.arrival.assistant.ArrivalStepsTool
import com.sprintstart.sprintstartbackend.arrival.assistant.TeammatesTool
import com.sprintstart.sprintstartbackend.assistant.external.BuddyAppGuideTools
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.board.assistant.OpenPullRequestsTool
import com.sprintstart.sprintstartbackend.insights.assistant.MyMetricsTool
import com.sprintstart.sprintstartbackend.knowledge.assistant.CanonicalAnswersTool
import com.sprintstart.sprintstartbackend.path.assistant.MyPathTool
import com.sprintstart.sprintstartbackend.starterwork.assistant.MyCompetenciesTool
import com.sprintstart.sprintstartbackend.starterwork.assistant.SuggestedTasksTool
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

    private val service = BuddySuggestionService(
        buddyToolExecutor,
        userApi,
        listOf(
            MyPathTool(mockk()),
            ArrivalStepsTool(mockk()),
            MyMetricsTool(mockk(), mockk()),
            MyCompetenciesTool(mockk()),
            OpenPullRequestsTool(mockk(), mockk()),
            SuggestedTasksTool(mockk(), mockk()),
            TeammatesTool(mockk(), mockk()),
            BuddyAppGuideTools(mockk()),
        ),
    )

    private val userId = UUID.randomUUID()
    private val authId = "auth-1"

    private fun spec(name: String) = BuddyToolSpecDto(name = name, description = "", parameters = buildJsonObject { })

    private fun mounted(vararg names: String) {
        every { buddyToolExecutor.toolSpecs(userId) } returns names.map(::spec)
    }

    @Test
    fun `offers a chip for each mounted tool it has wording for`() {
        mounted(
            MyMetricsTool.GET_MY_METRICS,
            SuggestedTasksTool.GET_SUGGESTED_TASKS,
        )

        assertThat(service.forHire(userId).map { it.label })
            .containsExactly("How's my work going?", "Anything I can pick up?")
    }

    @Test
    fun `offers the path chip only for a hire who has an onboarding path`() {
        mounted(MyMetricsTool.GET_MY_METRICS)
        assertThat(service.forHire(userId).map { it.label }).doesNotContain("Where am I on my path?")

        // The read tool is mounted only for a hire who has a path, so the chip inherits that gate --
        // nobody is offered a doorway into a plan they have not got.
        mounted(MyPathTool.READ_MY_PATH, MyMetricsTool.GET_MY_METRICS)
        assertThat(service.forHire(userId).map { it.label }).contains("Where am I on my path?")
    }

    @Test
    fun `offers to explain the page the hire is on`() {
        mounted(BuddyAppGuideTools.GET_APP_GUIDE)

        assertThat(service.forHire(userId).single().question).isEqualTo("What can I do on this page?")
    }

    /**
     * The whole point of deriving rather than listing: a chip appears exactly when its tool does.
     * Unlike the tool, a chip is something the hire *sees*, so getting this wrong is louder.
     */
    @Test
    fun `does not offer the pull-request chip when the tool is not mounted`() {
        mounted(
            MyMetricsTool.GET_MY_METRICS,
            MyCompetenciesTool.GET_MY_COMPETENCIES,
            SuggestedTasksTool.GET_SUGGESTED_TASKS,
        )

        assertThat(service.forHire(userId).map { it.label }).doesNotContain("Is my PR stuck?")
    }

    @Test
    fun `offers the pull-request chip when the tool is mounted`() {
        mounted(OpenPullRequestsTool.GET_MY_OPEN_PULL_REQUESTS)

        assertThat(service.forHire(userId))
            .singleElement()
            .satisfies({ assertThat(it.question).contains("pull request") })
    }

    /**
     * A fixed chip is out of reach of the contribution vocabulary, so every chip here must be
     * true of a designer and a Scrum Master too. Only the pull-request chip may speak that
     * language, and only where its own tool is what put it there.
     */
    @Test
    fun `no chip a non-developer sees mentions developer work`() {
        mounted(
            ArrivalStepsTool.GET_ARRIVAL_STEPS,
            MyMetricsTool.GET_MY_METRICS,
            MyCompetenciesTool.GET_MY_COMPETENCIES,
            SuggestedTasksTool.GET_SUGGESTED_TASKS,
            TeammatesTool.GET_TEAMMATES,
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
     * The path is first in the spec list because it is the onboarding, and setup comes before how
     * their work is going. Chips inherit that ordering rather than having an opinion of their own —
     * two orderings would eventually disagree.
     */
    @Test
    fun `keeps the order the tools are mounted in`() {
        mounted(
            MyPathTool.READ_MY_PATH,
            ArrivalStepsTool.GET_ARRIVAL_STEPS,
            MyMetricsTool.GET_MY_METRICS,
            SuggestedTasksTool.GET_SUGGESTED_TASKS,
        )

        assertThat(service.forHire(userId).map { it.label }).containsExactly(
            "Where am I on my path?",
            "What do I still need?",
            "How's my work going?",
            "Anything I can pick up?",
        )
    }

    /** "How is my onboarding going?" belongs to the path; the metrics chip must not ask it. */
    @Test
    fun `the metrics chip asks about work, not onboarding`() {
        mounted(MyMetricsTool.GET_MY_METRICS)

        assertThat(service.forHire(userId)).singleElement().satisfies({
            assertThat((it.label + " " + it.question).lowercase()).doesNotContain("onboarding")
        })
    }

    /**
     * The catalog is opt-in: a tool with no wording contributes nothing. Adding a tool must not
     * silently add a chip — what the mentor may read and what a hire is invited to ask are separate
     * decisions, and only one of them is visible to the hire.
     */
    @Test
    fun `ignores mounted tools with no chip of their own`() {
        mounted(CanonicalAnswersTool.SEARCH_CANONICAL_ANSWERS, "place_card")

        assertThat(service.forHire(userId)).isEmpty()
    }

    @Test
    fun `resolves the caller before reading their tools`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        mounted(SuggestedTasksTool.GET_SUGGESTED_TASKS)

        assertThat(service.forMe(authId).map { it.label }).containsExactly("Anything I can pick up?")
    }

    @Test
    fun `offers nothing when the caller resolves to no user`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

        assertThat(service.forMe(authId)).isEmpty()
    }
}
