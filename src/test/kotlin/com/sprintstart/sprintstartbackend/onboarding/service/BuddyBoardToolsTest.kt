package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class BuddyBoardToolsTest {
    private val boardService: BoardService = mockk()
    private val userApi: UserApi = mockk()
    private val tools = BuddyBoardTools(boardService, userApi)

    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith(project("Apollo")))
    }

    private fun project(name: String, id: UUID = projectId) = ProjectDto(projectId = id, name = name, description = "")

    private fun userWith(vararg projects: ProjectDto) = UserDto(
        id = userId,
        username = "hire",
        firstname = "Sam",
        lastname = "Hire",
        avatarUrl = null,
        profileIcon = null,
        projects = projects.toSet(),
        projectRoles = emptyList(),
    )

    private fun placeCall(kind: String, subject: String? = null) = BuddyToolCallDto(
        id = "c0",
        name = "place_card",
        arguments = buildJsonObject {
            put("kind", kind)
            subject?.let { put("subject", it) }
        },
    )

    @Test
    fun `a diagram carries the question the mentor chose`() {
        every { boardService.place(userId, projectId, BoardCardKind.DIAGRAM, "how auth flows here") } returns
            BoardService.PlacementOutcome.PLACED

        val result = tools.execute(placeCall("DIAGRAM", "how auth flows here"), userId)

        // The one argument any kind takes beyond its kind: the model chooses the question, and the
        // picture is still derived from the project's own material.
        assertThat(result).contains("Placed")
        verify { boardService.place(userId, projectId, BoardCardKind.DIAGRAM, "how auth flows here") }
    }

    @Test
    fun `a diagram with no subject comes back as a sentence, not silence`() {
        every { boardService.place(userId, projectId, BoardCardKind.DIAGRAM, null) } returns
            BoardService.PlacementOutcome.NEEDS_A_SUBJECT

        val result = tools.execute(placeCall("DIAGRAM"), userId)

        // A tool that fails quietly is a tool the model reports as having worked.
        assertThat(result).contains("diagram of something")
        assertThat(result).contains("how a request reaches the database")
    }

    @Test
    fun `the tool advertises subject as belonging to diagrams only`() {
        val spec = tools.toolSpecs().single()

        val subject = spec.parameters["properties"]!!.jsonObject["subject"]!!.jsonObject
        assertThat(subject["description"]!!.jsonPrimitive.content).contains("DIAGRAM only")
        // Not required at the schema level: every other kind takes no subject at all, and a schema
        // demanding one would make them all invalid.
        assertThat(spec.parameters["required"]!!.jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("kind")
    }

    @Test
    fun `places a card and tells the mentor it will stay there`() {
        every { boardService.place(userId, projectId, BoardCardKind.SUGGESTED_TASKS) } returns
            BoardService.PlacementOutcome.PLACED

        val result = tools.execute(placeCall("SUGGESTED_TASKS"), userId)

        assertThat(result).contains("Placed")
        assertThat(result).contains("Apollo")
        verify { boardService.place(userId, projectId, BoardCardKind.SUGGESTED_TASKS) }
    }

    @Test
    fun `a card already there is not reported as newly added`() {
        every { boardService.place(any(), any(), any()) } returns
            BoardService.PlacementOutcome.ALREADY_THERE

        // The distinction matters: a mentor that cannot tell "added" from "was already there" will
        // claim it added something on every turn.
        assertThat(tools.execute(placeCall("CURRENT_TASK"), userId))
            .contains("already on their board")
    }

    @Test
    fun `a dismissed card comes back as a refusal the mentor is told not to retry`() {
        every { boardService.place(any(), any(), any()) } returns
            BoardService.PlacementOutcome.DISMISSED_BY_HIRE

        val result = tools.execute(placeCall("SUGGESTED_TASKS"), userId)

        assertThat(result).contains("took that card off")
        assertThat(result).contains("Do not add it")
    }

    @Test
    fun `a card the track cannot fill is refused and the mentor is told not to mention it`() {
        every { boardService.place(any(), any(), any()) } returns
            BoardService.PlacementOutcome.UNSUPPORTED

        assertThat(tools.execute(placeCall("CURRENT_TASK"), userId)).contains("Do not mention it")
    }

    @Test
    fun `the mentor cannot place a card the board already keeps by itself`() {
        val result = tools.execute(placeCall("PATH_TO_FIRST_CONTRIBUTION"), userId)

        // Offering a baseline kind would only let the model take credit for a card that was there
        // anyway.
        assertThat(result).contains("not a card I can place")
        verify(exactly = 0) { boardService.place(any(), any(), any()) }
    }

    @Test
    fun `an unrecognised kind lists the ones that exist`() {
        val result = tools.execute(placeCall("A_DIAGRAM_OF_MY_FEELINGS"), userId)

        assertThat(result).contains("CURRENT_TASK")
        assertThat(result).contains("SUGGESTED_TASKS")
        verify(exactly = 0) { boardService.place(any(), any(), any()) }
    }

    @Test
    fun `a hire on more than one project is asked which board, not guessed at`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(
            userWith(project("Apollo"), project("Gemini", UUID.randomUUID())),
        )

        assertThat(tools.execute(placeCall("CURRENT_TASK"), userId)).contains("Ask which one")
        verify(exactly = 0) { boardService.place(any(), any(), any()) }
    }

    @Test
    fun `a hire on no project has no board to put anything on`() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(userWith())

        assertThat(tools.execute(placeCall("CURRENT_TASK"), userId)).contains("not on a project")
        verify(exactly = 0) { boardService.place(any(), any(), any()) }
    }

    @Test
    fun `the tool offers only the kinds the board does not keep by itself`() {
        val spec = tools.toolSpecs().single()

        assertThat(spec.name).isEqualTo("place_card")
        assertThat(spec.description).contains("CURRENT_TASK", "SUGGESTED_TASKS")
        // The description must not offer a baseline card as something to place.
        assertThat(spec.description).doesNotContain("PATH_TO_FIRST_CONTRIBUTION")
    }
}
