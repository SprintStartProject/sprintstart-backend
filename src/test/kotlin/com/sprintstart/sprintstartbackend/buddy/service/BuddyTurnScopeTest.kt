package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.arrival.external.ContributionWording
import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class BuddyTurnScopeTest {
    private val userApi = mockk<UserApi>()
    private val projectScope = BuddyProjectScope(userApi)
    private val userId = UUID.randomUUID()

    private fun userOn(vararg projectIds: UUID) = UserDto(
        id = userId,
        username = "hire",
        firstname = "Sam",
        lastname = "Hire",
        avatarUrl = null,
        profileIcon = null,
        projects = projectIds.map { ProjectDto(projectId = it, name = it.toString(), description = "") }.toSet(),
        projectRoles = emptyList(),
    )

    @Test
    fun `a hire's projects are every project they are on`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userOn(a, b))

        assertThat(projectScope.projectIdsFor(UserId(userId))).containsExactlyInAnyOrder(a.toString(), b.toString())
    }

    @Test
    fun `an unknown user is on no project`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns emptyList()

        assertThat(projectScope.projectIdsFor(UserId(userId))).isEmpty()
    }

    @Test
    fun `only a single project is bound`() {
        val a = UUID.randomUUID()

        assertThat(projectScope.singleProjectOf(listOf(a.toString()))).isEqualTo(a)
        assertThat(projectScope.singleProjectOf(listOf(a.toString(), UUID.randomUUID().toString()))).isNull()
        assertThat(projectScope.singleProjectOf(emptyList())).isNull()
    }

    @Test
    fun `the vocabulary carries the contribution wording`() {
        val vocabulary = buddyVocabulary()

        assertThat(vocabulary.contributionNoun).isEqualTo(ContributionWording.NOUN)
        assertThat(vocabulary.contributionNounPlural).isEqualTo(ContributionWording.NOUN_PLURAL)
        assertThat(vocabulary.contributionVerbPast).isEqualTo(ContributionWording.VERB_PAST)
    }

    @Test
    fun `the summary goes on the first hop only while the scope goes on every hop`() {
        val session = BuddySession(userId = userId, summary = "so far")
        val scope = BuddyTurnScope(
            tools = emptyList(),
            vocabulary = buddyVocabulary(),
            projectIds = listOf("p1"),
            capabilitiesEnabled = false,
            filters = null,
        )
        val messages = listOf(BuddyAgentMessageDto(role = BuddyAgentMessageDto.ROLE_USER, content = "hi"))

        val first = buddyAgentRequest(messages, 1, session, scope)
        val second = buddyAgentRequest(messages, 2, session, scope)

        assertThat(first.priorSummary).isEqualTo("so far")
        assertThat(second.priorSummary).isNull()
        assertThat(second.projectIds).containsExactly("p1")
        assertThat(second.capabilitiesEnabled).isFalse()
        assertThat(second.vocabulary).isEqualTo(scope.vocabulary)
        assertThat(second.messages).isEqualTo(messages)
    }
}
