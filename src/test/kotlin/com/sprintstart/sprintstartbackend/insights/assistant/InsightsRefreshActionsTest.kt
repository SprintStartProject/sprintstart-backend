package com.sprintstart.sprintstartbackend.insights.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.TeamActionDraft
import com.sprintstart.sprintstartbackend.assistant.external.TeamToolContext
import com.sprintstart.sprintstartbackend.insights.external.InsightsRefreshApi
import com.sprintstart.sprintstartbackend.insights.external.KnowledgeGapsRefresh
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class InsightsRefreshActionsTest {
    private val insightsRefreshApi: InsightsRefreshApi = mockk()

    private val projectId = UUID.randomUUID()
    private val context = TeamToolContext(userId = UUID.randomUUID(), authId = "auth|pm", projectId = projectId)

    private fun call(name: String) = BuddyToolCallDto(id = "c1", name = name, arguments = JsonObject(emptyMap()))

    private fun TeamActionDraft.proposed(): TeamActionDraft.Proposed {
        assertThat(this).isInstanceOf(TeamActionDraft.Proposed::class.java)
        return this as TeamActionDraft.Proposed
    }

    @Test
    fun `refreshing the FAQ is bulk and reports the groups it stored`() = runTest {
        val refresh = RefreshFaqAction(insightsRefreshApi)
        coEvery { insightsRefreshApi.refreshFaq(projectId) } returns 6

        val proposed = refresh.draft(call("refresh_faq"), context).proposed()
        val result = refresh.perform(proposed.params, context)

        assertThat(refresh.risk).isEqualTo(BuddyProposalRisk.BULK)
        assertThat(proposed.preview).contains("can take a while")
        assertThat(result).contains("6 recurring-question groups")
    }

    @Test
    fun `rescanning for gaps reports components and gaps for the turn's project`() = runTest {
        val refresh = RefreshKnowledgeGapsAction(insightsRefreshApi)
        coEvery { insightsRefreshApi.refreshKnowledgeGaps(projectId) } returns
            KnowledgeGapsRefresh(gapCount = 2, componentCount = 9)

        val result = refresh.perform(refresh.draft(call("refresh_knowledge_gaps"), context).proposed().params, context)

        assertThat(result).contains("9 components").contains("2 are missing documentation")
    }

    /** Nothing can change between the offer and the confirm, so neither refresh ever refuses at the confirm. */
    @Test
    fun `a refresh never refuses at the confirm`() {
        listOf(RefreshFaqAction(insightsRefreshApi), RefreshKnowledgeGapsAction(insightsRefreshApi)).forEach {
            assertThat(it.recheck(JsonObject(emptyMap()), context)).isNull()
        }
    }
}
