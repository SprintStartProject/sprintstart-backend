package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import java.util.Optional
import java.util.UUID

/**
 * A project with one member, and a [ContentScope] over it, for the content actions' tests.
 *
 * The scope is the real one over mocked sources, so a test of an action also tests that the action
 * asks it — an action that forgot to would find its target and fail the outsider test.
 */
internal class ContentFixture {
    val pathElements: PathElements = mockk(relaxed = true)
    val projectMembershipApi: ProjectMembershipApi = mockk(relaxed = true)
    val userApi: UserApi = mockk(relaxed = true)
    val proposals: StarterWorkTaskProposalRepository = mockk(relaxed = true)
    val starterWorkScope: StarterWorkScope = mockk(relaxed = true)
    val scope = ContentScope(pathElements, projectMembershipApi, userApi, proposals, starterWorkScope)

    val projectId: UUID = UUID.randomUUID()
    val memberId: UUID = UUID.randomUUID()
    val outsiderId: UUID = UUID.randomUUID()
    val context = TeamToolContext(userId = UUID.randomUUID(), authId = "auth|pm", projectId = projectId)

    init {
        every { projectMembershipApi.getProjectMembers(projectId) } returns
            listOf(ProjectMember(memberId, "Sam Rivera", githubLogin = null, joinedAt = null))
        onlyThisProject()
        every { proposals.findById(any()) } returns Optional.empty()
    }

    /** The member is on this project and no other, which is what most tests want. */
    fun onlyThisProject() = alsoOn()

    /** The member is on this project and on the projects named. */
    fun alsoOn(vararg names: String) {
        every { userApi.getUsersByIds(listOf(memberId)) } returns
            listOf(
                UserDto(
                    id = memberId,
                    username = "sam",
                    firstname = "Sam",
                    lastname = "Rivera",
                    avatarUrl = null,
                    profileIcon = null,
                    projects = setOf(ProjectDto(projectId, "This one", null)) +
                        names.map { ProjectDto(UUID.randomUUID(), it, null) },
                    projectRoles = emptyList(),
                ),
            )
    }

    /** An element that exists, on [owner]'s path. */
    fun element(
        kind: PathElementKind,
        id: UUID = UUID.randomUUID(),
        owner: UUID = memberId,
        title: String = "The ${kind.noun}",
        position: Int? = 0,
        children: Int = 0,
        contains: String = "",
        stepStatus: StepStatus? = null,
        finishedSteps: Int = 0,
        siblings: Int = 0,
    ): PathElement {
        val element =
            PathElement(kind, id, owner, title, position, children, contains, stepStatus, finishedSteps, siblings)
        every { pathElements.find(kind, id) } returns element
        return element
    }

    /** A starter-work task, from a repository that is linked to this project or not. */
    fun task(linked: Boolean = true, title: String = "Fix the typo"): StarterWorkTaskProposal {
        val task = StarterWorkTaskProposal(sourceId = "github:acme/app:ISSUE:${UUID.randomUUID()}", title = title)
        every { proposals.findById(task.id) } returns Optional.of(task)
        every { starterWorkScope.covers(task.sourceId, projectId) } returns linked
        return task
    }

    /** An element that is gone. */
    fun gone(kind: PathElementKind, id: UUID) {
        every { pathElements.find(kind, id) } returns null
    }

    fun call(name: String, vararg args: Pair<String, Any?>) =
        BuddyToolCallDto(id = "c1", name = name, arguments = json(*args))

    fun json(vararg args: Pair<String, Any?>): JsonObject =
        buildJsonObject {
            args.forEach { (key, value) ->
                when (value) {
                    null -> put(key, JsonNull)
                    is Number -> put(key, value)
                    is JsonElement -> put(key, value)
                    else -> put(key, JsonPrimitive(value.toString()))
                }
            }
        }

    fun proposed(draft: TeamActionDraft): TeamActionDraft.Proposed {
        assertThat(draft).isInstanceOf(TeamActionDraft.Proposed::class.java)
        return draft as TeamActionDraft.Proposed
    }

    fun refusal(draft: TeamActionDraft): String {
        assertThat(draft).isInstanceOf(TeamActionDraft.Refused::class.java)
        return (draft as TeamActionDraft.Refused).reason
    }
}
