package com.sprintstart.sprintstartbackend.board.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.HireTool
import com.sprintstart.sprintstartbackend.assistant.external.HireToolContext
import com.sprintstart.sprintstartbackend.assistant.external.HireToolOrder
import com.sprintstart.sprintstartbackend.board.external.HireBoardActionsApi
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.board.external.enums.PlacementOutcome
import com.sprintstart.sprintstartbackend.board.external.model.HireBoard
import com.sprintstart.sprintstartbackend.path.external.HirePathApi
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Puts something on the hire's board where they will still find it tomorrow, for the Buddy.
 *
 * Not an action tool. Every action proposes and waits for a button because each changes the hire's
 * onboarding. Placing a card changes what is on a page, so this applies immediately, attributed and
 * dismissible.
 *
 * `place_card` cannot invent anything. It takes a `kind` from a closed catalog and nothing else
 * — no content, title or caption. Whatever the card ends up saying is read server-side from the
 * same services the buddy's read tools use. Content the buddy writes, and every change it makes to
 * what the hire already has, goes through the confirm-gated board actions instead — see [PLACEABLE]
 * for where that line is.
 *
 * `DIAGRAM`'s `subject` is the one extension, and not a foothold for a second. It aims
 * retrieval and is asserted nowhere: every box comes back derived from the project's corpus with
 * the citation proving it, and an ungrounded box is dropped.
 *
 * Every outcome comes back as a sentence rather than as silence, and the refusals say what the
 * mentor should do instead. A tool that fails quietly is a tool the model reports as having worked.
 */
@Component
@Order(HireToolOrder.PLACE_CARD)
class PlaceCardTool(
    private val boardActions: HireBoardActionsApi,
    private val userApi: UserApi,
    private val hirePathApi: HirePathApi,
) : HireTool {
    override val spec = PLACE_CARD_SPEC

    override fun execute(call: BuddyToolCallDto, context: HireToolContext): String = placeCard(call, context.hire)

    private fun placeCard(call: BuddyToolCallDto, userId: UserId): String {
        val kind = call.kindArg()
            ?: return "That is not a card I can place. The kinds are: ${placeableKindNames()}."

        val project = when (val choice = userApi.soleProjectOf(userId)) {
            is ProjectChoice.Refused -> return choice.reason
            is ProjectChoice.One -> choice
        }

        return when (boardActions.place(HireBoard(userId, project.projectId), kind, call.subjectArg())) {
            PlacementOutcome.PLACED ->
                "Placed the $kind card on the hire's board for ${project.name}. Tell them it is " +
                    "there and will stay there — they can dismiss it if they do not want it."
            PlacementOutcome.ALREADY_THERE ->
                "That card is already on their board, so nothing changed. Point them at it rather " +
                    "than saying you added it."
            PlacementOutcome.DISMISSED_BY_HIRE ->
                "The hire took that card off their board, so it was not put back. Do not add it " +
                    "again — if it matters, say it in the conversation instead."
            PlacementOutcome.NOT_A_MEMBER ->
                "The hire is not a member of that project, so there is no board to put a card on."
            PlacementOutcome.NEEDS_A_SUBJECT ->
                "That kind needs a subject and none was given, so nothing was placed. For a " +
                    "diagram, try again with the question it should answer — for example \"how a " +
                    "request reaches the database\". For a path step, try again with the step's " +
                    "title."
            PlacementOutcome.NO_SUCH_STEP -> noSuchStepMessage(userId)
        }
    }

    /**
     * What to say when a `PATH_STEP` subject named nothing real, naming the hire's actual step
     * titles so the model can retry correctly without a separate read tool.
     *
     * Capped at [LIST_LIMIT] for the reason every other list here is: a mentor reading out every
     * step on a long path has turned a conversation into a listing.
     */
    private fun noSuchStepMessage(userId: UserId): String {
        val titles = hirePathApi.stepTitlesOf(userId)
        if (titles.isEmpty()) {
            return "The hire has no path yet, so there is no step to put on their board."
        }
        val named = titles.take(LIST_LIMIT).joinToString(", ")
        val suffix = if (titles.size > LIST_LIMIT) ", and more" else ""
        return "That is not one of the hire's steps, so nothing was placed. Their actual steps " +
            "are: $named$suffix. Try again with one of those."
    }

    /** Reads the `kind` argument, or null when it is missing or not a kind the buddy may place. */
    private fun BuddyToolCallDto.kindArg(): BoardCardKind? {
        val raw = (arguments["kind"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return PLACEABLE.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }

    /**
     * Reads the `subject` argument — the question a diagram answers, or the title of a path step.
     *
     * Passed straight through to [HireBoardActionsApi.place], which ignores it for every kind that does not
     * take one. A subject sent alongside `CURRENT_TASK` is not an error worth a sentence; it is a
     * model being verbose, and the card is unaffected either way.
     */
    private fun BuddyToolCallDto.subjectArg(): String? =
        (arguments["subject"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        const val PLACE_CARD = "place_card"

        /**
         * The kinds the mentor may place *here*, and only those.
         *
         * A baseline card is on the board already, so offering it would only let the model claim
         * credit for something that was there anyway. The authored kinds stay out of this list for
         * a stronger reason: their content is words the mentor chose, and every card this tool
         * places appears with no confirmation at all.
         *
         * **That is the line, and it is about the confirm rather than about the kind.** The mentor
         * can do anything to the hire's board that the hire can — write a note, link or checklist,
         * edit one, tick it, take cards off, rearrange them — through the board's own actions in
         * this package. Each of those proposes, shows the
         * hire what would change, writes only on their click, and marks the card as the mentor's
         * change afterwards. So the no-confirm tool stays limited to live reads, whose contents it
         * does not choose, and every change to content arrives through a gate the hire opens.
         * Widening this list would be the hole; the actions are not one.
         */
        private val PLACEABLE =
            BoardCardKind.entries.filter { it.placement == BoardCardKind.Placement.MENTOR }

        private fun placeableKindNames() = PLACEABLE.joinToString(", ") { it.name }

        val PLACE_CARD_SPEC = BuddyToolSpecDto(
            name = PLACE_CARD,
            description = "Put a card on the hire's board — the page where things stay put between " +
                "conversations, since a new conversation does not see the earlier ones. Use it when something " +
                "you have just discussed is worth them still having tomorrow: after they pick a " +
                "task to work on (CURRENT_TASK), or when they are looking for work and you have " +
                "shown them suggestions (SUGGESTED_TASKS), after explaining how some part of the " +
                "system fits together (DIAGRAM), or when they are working through a step of their " +
                "path and it is worth having its tasks, expected outcome and resources pinned " +
                "where they will still find them tomorrow (PATH_STEP). This applies straight away " +
                "— no confirmation — and the card is clearly marked as yours and easy for them to " +
                "dismiss. You choose *that* a card belongs there; you never choose what it says, " +
                "because its contents are read live from the same place your other tools read — " +
                "for PATH_STEP that includes the tasks, which the hire can tick right on the card " +
                "and it will still agree with their path page. Kinds: " + placeableKindNames() +
                ". Do not place a card they have already dismissed, and do not place one just to " +
                "have placed something. This is not the only way to change their board, so never " +
                "tell them you cannot put your own words on it or change what is there: " +
                "place_checklist, place_note and place_link keep something you wrote, and " +
                "edit_note, edit_link, edit_checklist, dismiss_cards and reorder_cards change what " +
                "they already have. Each of those shows them what would change and applies it " +
                "when they confirm.",
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
                            "Required for DIAGRAM and PATH_STEP, ignored for every other kind. " +
                                "For DIAGRAM: what the diagram should be a diagram of, phrased as " +
                                "the question it answers — \"how a request reaches the database\", " +
                                "\"what the ingestion pipeline is made of\". You are choosing the " +
                                "question, not the answer: the picture is drawn from this " +
                                "project's own material, every box carries the source it came " +
                                "from, and anything the material does not support is left out. So " +
                                "ask about something this project actually has, and use the names " +
                                "it uses. For PATH_STEP: the title of the step, exactly as the hire " +
                                "sees it on their path — a wrong title fails and lists the real " +
                                "ones back, so retry with one of those.",
                        )
                    }
                }
                putJsonArray("required") { add("kind") }
            },
        )
    }
}
