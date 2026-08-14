package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The buddy's board tool: putting something where the hire will still find it tomorrow.
 *
 * Not an action tool. Every tool in `BuddyActionService` proposes and waits for a button
 * because each changes the hire's onboarding. Placing a card changes what is on a page, so this
 * applies immediately, attributed and dismissible.
 *
 * It cannot invent anything. The tool takes a `kind` from a closed catalog and nothing else
 * — no content, title or caption. Whatever the card ends up saying is read server-side from the
 * same services the buddy's read tools use.
 *
 * `DIAGRAM`'s `subject` is the one extension, and not a foothold for a second. It aims
 * retrieval and is asserted nowhere: every box comes back derived from the project's corpus with
 * the citation proving it, and an ungrounded box is dropped.
 */
@Component
class BuddyBoardTools(
    private val boardService: BoardService,
    private val userApi: UserApi,
) {
    /** The tool specs this component owns, aggregated into the buddy's catalog by the executor. */
    fun toolSpecs(): List<BuddyToolSpecDto> = listOf(PLACE_CARD_SPEC)

    /** Whether [toolName] is one of this component's tools. */
    fun handles(toolName: String): Boolean = toolName == PLACE_CARD

    /**
     * Places a card on [userId]'s board, returning a plain-text result for the model.
     *
     * Every outcome comes back as a sentence rather than as silence, and the refusals say what the
     * mentor should do instead. A tool that fails quietly is a tool the model reports as having
     * worked.
     */
    fun execute(call: BuddyToolCallDto, userId: UUID): String {
        val kind = call.kindArg()
            ?: return "That is not a card I can place. The kinds are: ${placeableKindNames()}."

        val projects = userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()
        // Scoped like every action the buddy takes: a board belongs to one project, and guessing
        // which one somebody meant is how a card lands on the wrong board.
        val project = when (projects.size) {
            0 -> return "The hire is not on a project yet, so there is no board to put a card on."
            1 -> projects.first()
            else ->
                return "The hire is onboarding on more than one project. Ask which one before " +
                    "putting anything on their board."
        }

        return when (boardService.place(userId, project.projectId, kind, call.subjectArg())) {
            BoardService.PlacementOutcome.PLACED ->
                "Placed the $kind card on the hire's board for ${project.name}. Tell them it is " +
                    "there and will stay there — they can dismiss it if they do not want it."
            BoardService.PlacementOutcome.ALREADY_THERE ->
                "That card is already on their board, so nothing changed. Point them at it rather " +
                    "than saying you added it."
            BoardService.PlacementOutcome.DISMISSED_BY_HIRE ->
                "The hire took that card off their board, so it was not put back. Do not add it " +
                    "again — if it matters, say it in the conversation instead."
            BoardService.PlacementOutcome.NOT_A_MEMBER ->
                "The hire is not a member of that project, so there is no board to put a card on."
            BoardService.PlacementOutcome.NEEDS_A_SUBJECT ->
                "A diagram has to be a diagram of something, and no subject was given, so nothing " +
                    "was placed. Try again with the question it should answer — for example " +
                    "\"how a request reaches the database\"."
        }
    }

    /** Reads the `kind` argument, or null when it is missing or not a kind the buddy may place. */
    private fun BuddyToolCallDto.kindArg(): BoardCardKind? {
        val raw = (arguments["kind"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return PLACEABLE.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }

    /**
     * Reads the `subject` argument — the question a diagram answers, and only ever that.
     *
     * Passed straight through to [BoardService.place], which ignores it for every kind but
     * `DIAGRAM`. A subject sent alongside `CURRENT_TASK` is not an error worth a sentence; it is a
     * model being verbose, and the card is unaffected either way.
     */
    private fun BuddyToolCallDto.subjectArg(): String? =
        (arguments["subject"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        const val PLACE_CARD = "place_card"

        /**
         * The kinds the mentor may place, and only those.
         *
         * A baseline card is on the board already, so offering it would only let the model claim
         * credit for something that was there anyway. A card the *hire* wrote is theirs — the
         * mentor cannot create one, and the surest way to keep it that way is that no tool exists
         * which could.
         */
        private val PLACEABLE =
            BoardCardKind.entries.filter { it.placement == BoardCardKind.Placement.MENTOR }

        private fun placeableKindNames() = PLACEABLE.joinToString(", ") { it.name }

        val PLACE_CARD_SPEC = BuddyToolSpecDto(
            name = PLACE_CARD,
            description = "Put a card on the hire's board — the page where things stay put between " +
                "conversations, since this chat starts fresh every visit. Use it when something " +
                "you have just discussed is worth them still having tomorrow: after they pick a " +
                "task to work on (CURRENT_TASK), or when they are looking for work and you have " +
                "shown them suggestions (SUGGESTED_TASKS), or after explaining how some part of " +
                "the system fits together (DIAGRAM). This applies straight away — no " +
                "confirmation — and the card is clearly marked as yours and easy for them to " +
                "dismiss. You choose *that* a card belongs there; you never choose what it says, " +
                "because its contents are read live from the same place your other tools read. " +
                "Kinds: " + placeableKindNames() + ". Do not place a card they have already " +
                "dismissed, and do not place one just to have placed something.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("kind") {
                        put("type", "string")
                        putJsonArray("enum") { PLACEABLE.forEach { add(it.name) } }
                        put("description", "Which card to put on the board.")
                    }
                    putJsonObject("subject") {
                        put("type", "string")
                        put(
                            "description",
                            "DIAGRAM only, and required for it: what the diagram should be a " +
                                "diagram of, phrased as the question it answers — \"how a request " +
                                "reaches the database\", \"what the ingestion pipeline is made " +
                                "of\". You are choosing the question, not the answer: the picture " +
                                "is drawn from this project's own material, every box carries the " +
                                "source it came from, and anything the material does not support " +
                                "is left out. So ask about something this project actually has, " +
                                "and use the names it uses. Ignored for other kinds.",
                        )
                    }
                }
                putJsonArray("required") { add("kind") }
            },
        )
    }
}
