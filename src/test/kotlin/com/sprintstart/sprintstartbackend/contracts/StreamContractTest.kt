package com.sprintstart.sprintstartbackend.contracts

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyService
import com.sprintstart.sprintstartbackend.onboarding.service.ResolvedBuddyCitation
import com.sprintstart.sprintstartbackend.onboarding.service.emitCitationsAndDone
import com.sprintstart.sprintstartbackend.onboarding.service.endFailureWithErrorEvent
import com.sprintstart.sprintstartbackend.onboarding.service.relayAgentTurn
import com.sprintstart.sprintstartbackend.onboarding.service.toolUseEvent
import com.sprintstart.sprintstartbackend.shared.web.HttpClientFactory
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

/**
 * Both stream contracts of the buddy, checked from this side.
 *
 * - `agent-stream-events`: produced by sprintstart-ai, consumed here. Every shared example must decode with
 *   the parser production uses — a chunk that does not is skipped silently at runtime.
 * - `buddy-stream-events`: produced here, consumed by sprintstart-frontend. What the real relay code emits
 *   must match the schema the frontend reads.
 */
class StreamContractTest {
    private val agentParser = HttpClientFactory().jsonParser()

    @Test
    fun `every agent stream example decodes with the production parser`() {
        val decoded = examples("agent-stream").map { (name, json) ->
            name to agentParser.decodeFromString<BuddyAgentStreamEvent>(json)
        }

        assertThat(decoded).allSatisfy { (name, event) ->
            assertThat(event.type).`as`(name).isIn(HANDLED_AGENT_EVENTS)
        }
        assertThat(decoded.filter { it.second.type == BuddyAgentStreamEvent.RESULT })
            .isNotEmpty
            .allSatisfy { (_, event) -> event.toResponse() }
    }

    @Test
    fun `every agent stream event type is handled here`() {
        assertThat(eventTypes("agent-stream-events")).isSubsetOf(HANDLED_AGENT_EVENTS)
    }

    @Test
    fun `every buddy stream example follows the schema and every type has one`() {
        val schema = schema("buddy-stream-events")
        val examples = examples("buddy-stream")

        assertThat(examples).allSatisfy { (name, json) -> assertThat(errors(schema, json)).`as`(name).isEmpty() }
        assertThat(examples.map { ObjectMapper().readTree(it.second)["type"].asText() }.toSet())
            .isEqualTo(eventTypes("buddy-stream-events"))
    }

    @Test
    fun `what the relay emits for a whole agent turn follows the buddy stream schema`() {
        val schema = schema("buddy-stream-events")
        val events = runBlocking { relayedTurn() + failedTurn() } + representativeServiceEvents()

        assertThat(events.map { it.type }.toSet()).isEqualTo(eventTypes("buddy-stream-events"))
        assertThat(events).allSatisfy { event ->
            val json = Json.encodeToString(event)
            assertThat(errors(schema, json)).`as`(json).isEmpty()
        }
    }

    private suspend fun relayedTurn(): List<BuddyStreamEvent> {
        val agentEvents = examples("agent-stream")
            .map { agentParser.decodeFromString<BuddyAgentStreamEvent>(it.second) }
            .filter { it.type != BuddyAgentStreamEvent.ERROR }
        val citation = ResolvedBuddyCitation(UUID.randomUUID(), "handbook.md", null, 12, null)
        return flow {
            relayAgentTurn(agentEvents.asFlow(), StringBuilder())
            emit(toolUseEvent("get_my_path"))
            emitCitationsAndDone(listOf(citation))
        }.toList()
    }

    private suspend fun failedTurn(): List<BuddyStreamEvent> =
        flow<BuddyStreamEvent> { error("the AI went away") }.endFailureWithErrorEvent().toList()

    /** Built inside private service methods today, so constructed here the way those methods do. */
    private fun representativeServiceEvents(): List<BuddyStreamEvent> = listOf(
        BuddyStreamEvent(type = BuddyService.OPENING_ACTION, label = "Find me a task", question = "What next?"),
        BuddyStreamEvent(type = BuddyService.RESET),
        BuddyStreamEvent(
            type = BuddyService.ACTION_PROPOSAL,
            action = "dismiss_cards",
            label = "Dismiss 2 cards",
            cardIds = listOf(UUID.randomUUID().toString()),
            cardNames = listOf("Read the handbook"),
            proposalId = UUID.randomUUID().toString(),
            risk = "BULK",
        ),
    )

    private fun examples(folder: String): List<Pair<String, String>> =
        File("$CONTRACTS/examples/$folder")
            .listFiles { file -> file.extension == "json" }!!
            .sortedBy { it.name }
            .map { it.name to it.readText() }

    private fun schema(name: String): JsonSchema =
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(File("$CONTRACTS/$name.schema.json").readText())

    private fun eventTypes(name: String): Set<String> =
        ObjectMapper()
            .readTree(File("$CONTRACTS/$name.schema.json"))["oneOf"]
            .map { it["properties"]["type"]["const"].asText() }
            .toSet()

    private fun errors(schema: JsonSchema, json: String): List<String> =
        schema.validate(ObjectMapper().readTree(json)).map { it.message }

    private companion object {
        const val CONTRACTS = "contracts"
        val HANDLED_AGENT_EVENTS = setOf(
            BuddyAgentStreamEvent.REASONING,
            BuddyAgentStreamEvent.TOKEN,
            BuddyAgentStreamEvent.TOOL_USE,
            BuddyAgentStreamEvent.RESULT,
            BuddyAgentStreamEvent.ERROR,
        )
    }
}
