package com.sprintstart.sprintstartbackend.board.external

import com.sprintstart.sprintstartbackend.board.model.response.board.MemoryRecapContent
import com.sprintstart.sprintstartbackend.shared.ids.UserId

/**
 * What the buddy remembers of a hire, for the board's memory-recap card.
 *
 * The board shows it but the buddy owns it, so the board declares what it needs here and the buddy
 * implements it, rather than the board reading the buddy's sessions.
 */
fun interface MemoryRecapSource {
    /** The memory of the hire's latest conversation; empty before their first conversation is folded. */
    fun latestRecap(hire: UserId): MemoryRecapContent
}
