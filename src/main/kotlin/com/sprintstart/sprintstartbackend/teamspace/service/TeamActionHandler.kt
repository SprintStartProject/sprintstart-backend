package com.sprintstart.sprintstartbackend.teamspace.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * One action the team-mode buddy may offer a project's manager, in three steps that never blur.
 *
 * 1. [draft] turns the model's call into a proposal, or a reason it cannot be offered, and changes nothing.
 * 2. [recheck] runs when the manager confirms, against what was stored: the target may have changed since.
 * 3. [perform] makes the change.
 *
 * Every implementation must check that its target belongs to [TeamToolContext.projectId], because an id
 * in the call or the stored params came from the model.
 */
interface TeamActionHandler {
    val area: TeamArea

    val risk: BuddyProposalRisk

    /** The tool the model calls; its name identifies the action in stored proposals. */
    val spec: BuddyToolSpecDto

    fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft

    /**
     * @return `null` when the stored proposal can still run, otherwise the reason it cannot. An action
     * with nothing that can change between the preview and the confirm needs no override.
     */
    fun recheck(params: JsonObject, context: TeamToolContext): String? = null

    /**
     * Makes the change and returns the line to show the manager.
     *
     * @throws org.springframework.web.server.ResponseStatusException for a handled failure; its reason
     * is shown to the manager and the proposal is marked failed.
     */
    suspend fun perform(params: JsonObject, context: TeamToolContext): String
}

/** What [TeamActionHandler.draft] made of a call. */
sealed interface TeamActionDraft {
    /**
     * An action ready to offer.
     *
     * @property label The confirm button, naming the action and its target.
     * @property preview Everything the manager is agreeing to, in words.
     */
    data class Proposed(
        val params: JsonObject,
        val label: String,
        val preview: String,
    ) : TeamActionDraft

    /** Not offerable, with a reason the model can act on. */
    data class Refused(
        val reason: String,
    ) : TeamActionDraft
}

/**
 * A [TeamActionHandler] whose stored proposal is a typed [P] rather than a raw [JsonObject].
 *
 * [P] is written to JSON when the proposal is stored and read back when it is confirmed, so an action names
 * each of its fields once, in one class, and the compiler checks every use. The stored JSON is the same shape
 * a hand-built [JsonObject] had, and keeps whatever names [P] gives its fields with `@SerialName`.
 *
 * Params that cannot be read back are refused at the recheck, never run.
 */
abstract class TypedTeamAction<P : Any>(
    private val paramsSerializer: KSerializer<P>,
) : TeamActionHandler {
    /** A proposal carrying [params], for [draft] to return. */
    protected fun proposed(params: P, label: String, preview: String): TeamActionDraft.Proposed =
        TeamActionDraft.Proposed(
            params = StoredParams.json.encodeToJsonElement(paramsSerializer, params).jsonObject,
            label = label,
            preview = preview,
        )

    /** The typed form of [TeamActionHandler.recheck]. */
    open fun recheckParams(params: P, context: TeamToolContext): String? = null

    /** The typed form of [TeamActionHandler.perform]. */
    abstract suspend fun performParams(params: P, context: TeamToolContext): String

    final override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val typed = read(params) ?: return UNREADABLE
        return recheckParams(typed, context)
    }

    final override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val typed = read(params) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, UNREADABLE)
        return performParams(typed, context)
    }

    private fun read(params: JsonObject): P? =
        runCatching { StoredParams.json.decodeFromJsonElement(paramsSerializer, params) }.getOrNull()

    private companion object {
        const val UNREADABLE = "This proposal could not be read back, so nothing was changed."
    }
}

private const val LABEL_CHARS = 60

/** Short enough for a confirm button. */
internal fun String.forLabel(): String = if (length <= LABEL_CHARS) this else take(LABEL_CHARS - 1).trimEnd() + "…"
