package com.sprintstart.sprintstartbackend.buddy.external.enums

/** Who authored one [com.sprintstart.sprintstartbackend.buddy.model.entity.BuddyMessage]. */
enum class BuddyMessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

/** A role as the AI service spells it, for every transcript the buddy sends. */
fun BuddyMessageRole.toHistoryRole(): String =
    when (this) {
        BuddyMessageRole.USER -> "user"
        BuddyMessageRole.ASSISTANT -> "assistant"
        BuddyMessageRole.SYSTEM -> "system"
    }
