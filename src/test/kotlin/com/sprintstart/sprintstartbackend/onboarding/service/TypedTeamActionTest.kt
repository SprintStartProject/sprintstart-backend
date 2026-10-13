package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class TypedTeamActionTest {
    private val context = TeamToolContext(userId = UUID.randomUUID(), authId = "auth|pm", projectId = UUID.randomUUID())
    private val targetId = UUID.randomUUID()

    @Serializable
    private data class EditParams(
        @SerialName("target_id") @Serializable(with = UuidSerializer::class) val targetId: UUID,
        val title: String? = null,
        val description: String? = null,
        val position: Int? = null,
    )

    private class Edit : TypedTeamAction<EditParams>(EditParams.serializer()) {
        var seen: EditParams? = null
        override val area = TeamArea.CONTENT
        override val risk = BuddyProposalRisk.STANDARD
        override val spec = BuddyToolSpecDto(name = "edit", description = "", parameters = JsonObject(emptyMap()))

        override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft =
            throw UnsupportedOperationException()

        fun propose(params: EditParams): TeamActionDraft.Proposed = proposed(params, label = "l", preview = "p")

        override fun recheckParams(params: EditParams, context: TeamToolContext): String? {
            seen = params
            return null
        }

        override suspend fun performParams(params: EditParams, context: TeamToolContext): String {
            seen = params
            return "done"
        }
    }

    @Test
    fun `stores the fields under their serial names and leaves unset ones out`() {
        val stored = Edit().propose(EditParams(targetId, title = "New", position = 2)).params

        assertThat(stored.keys).containsExactlyInAnyOrder("target_id", "title", "position")
        assertThat(stored["target_id"]).isEqualTo(JsonPrimitive(targetId.toString()))
        assertThat(stored["position"]).isEqualTo(JsonPrimitive(2))
    }

    @Test
    fun `a blank field is stored, so taking a field off differs from leaving it alone`() {
        val stored = Edit().propose(EditParams(targetId, description = "")).params

        assertThat(stored.keys).containsExactlyInAnyOrder("target_id", "description")
    }

    @Test
    fun `reads back what it stored`() = runTest {
        val action = Edit()
        val params = EditParams(targetId, title = "New", description = "", position = 2)

        action.recheck(action.propose(params).params, context)

        assertThat(action.seen).isEqualTo(params)
    }

    @Test
    fun `reads a proposal stored with numbers as strings`() {
        val action = Edit()
        val legacy = buildJsonObject {
            put("target_id", targetId.toString())
            put("position", "3")
        }

        assertThat(action.recheck(legacy, context)).isNull()
        assertThat(action.seen).isEqualTo(EditParams(targetId, position = 3))
    }

    @Test
    fun `refuses params that cannot be read back, instead of running them`() = runTest {
        val action = Edit()
        val unreadable = buildJsonObject { put("target_id", "not-a-uuid") }

        assertThat(action.recheck(unreadable, context)).contains("could not be read back")
        assertThatThrownBy { kotlinx.coroutines.runBlocking { action.perform(unreadable, context) } }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            }
        assertThat(action.seen).isNull()
    }

    @Test
    fun `ignores fields it does not know`() {
        val action = Edit()
        val extra = buildJsonObject {
            put("target_id", targetId.toString())
            put("removed_in_a_later_version", "x")
        }

        assertThat(action.recheck(extra, context)).isNull()
    }
}
