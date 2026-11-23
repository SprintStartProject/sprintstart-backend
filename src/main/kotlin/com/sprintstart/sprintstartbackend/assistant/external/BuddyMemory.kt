package com.sprintstart.sprintstartbackend.assistant.external

import java.util.UUID

/**
 * The part of a buddy conversation that summarisation reads and writes.
 *
 * Both the hire's own Buddy session and a manager's team session keep a running memory note
 * and a cursor over their transcript, and both are folded by the same rules. This is the shape those
 * rules need, so the fold is written once rather than kept in step across two copies of its races.
 */
interface BuddyMemory {
    val id: UUID

    /** The AI-written running summary of the oldest [summarizedCount] messages. */
    var summary: String?

    /** How many of the oldest persisted messages [summary] covers. */
    var summarizedCount: Int
}
