package com.sprintstart.sprintstartbackend.assistant.external

/** The values of [BuddyStreamEvent.type]: the stream vocabulary the browser switches on. */
object BuddyStreamEventType {
    const val TOKEN = "token"
    const val DONE = "done"

    /** Terminal like DONE: the turn failed after its stream had started. */
    const val ERROR = "error"

    /** Team mode only: the words shown so far were not the answer and are to be dropped. */
    const val RESET = "reset"

    /**
     * The opening's suggested next step. Not [ACTION_PROPOSAL]: that is gated on the hire confirming,
     * whereas this only fills the composer with a question.
     */
    const val OPENING_ACTION = "opening_action"

    // A change offered for the user's confirmation, and the notice that a tool is running.
    const val ACTION_PROPOSAL = "action_proposal"
    const val TOOL_USE = "tool_use"
    const val TOOL_KIND = "tool"
}
