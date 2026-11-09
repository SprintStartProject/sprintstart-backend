package com.sprintstart.sprintstartbackend.assistant.external

import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId

/** Whether an action is about the hire as a person, or about their work on the one project they are on. */
enum class HireActionScope {
    /** Offered and run with no project: a GitHub login, a competency placement, the hire's own path. */
    PERSON,

    /** Needs the one project the hire is onboarding on; refused when they are on none or several. */
    PROJECT,
}

/** The one project a project-scoped action runs on. */
data class HireProject(
    val id: ProjectId,
    val name: String,
)

/**
 * Who an action runs for. [project] is set for a [HireActionScope.PROJECT] action and null for a
 * [HireActionScope.PERSON] one; the Buddy resolves it, so an action never guesses which project was meant.
 */
data class HireActionContext(
    val hire: UserId,
    val project: HireProject?,
) {
    /** The project of a [HireActionScope.PROJECT] action, which the Buddy always resolves before calling it. */
    val requireProject: HireProject get() = checkNotNull(project) { "A project-scoped action ran without a project" }
}

/**
 * An action a feature lends the hire's Buddy, from `<feature>/assistant/`: offered as a proposal the hire
 * confirms with a button, and run only on that confirm.
 *
 * [propose] never changes anything. Its proposal travels to the browser and comes back on the confirm as a
 * [HireActionRequest], so [perform] re-checks everything the request names against the caller rather than
 * trusting it. Every outcome a hire can act on comes back as a sentence: a refusal from [propose] is the tool
 * result the model reads, and one from [perform] is a [BuddyActionResponse] with `ok = false`.
 *
 * The Buddy offers every bean of this type that [isMountedFor] the caller, in `@Order` order (see
 * [HireActionOrder]).
 */
interface HireAction {
    val spec: BuddyToolSpecDto

    val scope: HireActionScope

    /** A verb phrase for the reason a project-scoped action cannot be offered, e.g. "claim a goal". */
    val gerund: String

    /** Whether to hand this action to [hire] at all; absent rather than offered when it could only refuse. */
    fun isMountedFor(hire: UserId): Boolean = true

    fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome

    /**
     * Runs the confirmed action as the signed-in hire behind [authId].
     *
     * @throws org.springframework.web.server.ResponseStatusException for a precondition the underlying
     * service owns; its reason is relayed to the hire as the action's sentence.
     */
    suspend fun perform(request: HireActionRequest, context: HireActionContext, authId: AuthId): BuddyActionResponse
}

/** Where each feature's hire actions sit in the list the reasoner sees, lowest first. */
object HireActionOrder {
    const val FLAG_TO_PM = 100
    const val REQUEST_ATTESTATION = 110
    const val SET_GITHUB_LOGIN = 120
    const val OPEN_ORIENTATION = 200
    const val CLAIM_GOAL = 300
    const val RECORD_ASSESSMENT = 600
    const val COMPLETE_STEP = 700
    const val COMPLETE_TASK = 710
    const val ANSWER_QUESTION = 720
    const val ADD_PATH_STEP = 730
    const val REQUEST_SKIP = 740
    const val PLACE_CHECKLIST = 800
    const val AMEND_CHECKLIST = 810
    const val TICK_CHECKLIST_ITEMS = 820
    const val REWORD_CHECKLIST_ITEM = 830
    const val PLACE_NOTE = 840
    const val PLACE_LINK = 850
    const val EDIT_NOTE = 860
    const val EDIT_LINK = 870
    const val EDIT_CHECKLIST = 880
    const val DISMISS_CARDS = 890
    const val REORDER_CARDS = 900
}

/**
 * What the model is told when it offers an action on [projectName], and what it must not do afterwards.
 *
 * The second half of this text is not decoration. **The model never finds out what happened to
 * a proposal**: the hire confirms out-of-band, the outcome lands in the client's own message
 * state, and nothing about it comes back into the conversation. So the model is blind here, and
 * a blind model that believes a button is still on screen starts pointing at it — a hire once
 * spent a whole exchange being told to click a button that had been spent on a failed confirm
 * several turns earlier, ending with the mentor guessing aloud where on their screen it might
 * be hiding. Telling somebody they must be missing something they can see is not there is the
 * worst thing this surface can do: it makes them distrust the app rather than the sentence.
 *
 * Hence the rule the text carries — never describe the button, and when the hire says it did
 * not work, call the tool again instead of insisting. A fresh proposal costs one click and puts
 * a real control back on screen; describing the old one cannot.
 */
fun offeredToHire(projectName: String, label: String): String =
    "Proposed to the hire on $projectName: “$label”. They will see a confirm " +
        "button; the action runs only if they click it. Offer it — do not claim it is done. " +
        "You will never be told whether they confirmed it or what came of it, and a confirmed " +
        "proposal leaves the screen. So do not describe the button, tell them where to find " +
        "it, or ask them to click it again. If they say nothing happened or they cannot see " +
        "it, believe them and call this tool again to offer it afresh."
