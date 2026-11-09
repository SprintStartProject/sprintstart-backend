package com.sprintstart.sprintstartbackend.board.model.mapper

import com.sprintstart.sprintstartbackend.board.model.entity.BoardCard
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardCardChangeResponse

/** All three attribution columns, or nothing — never a change with no author or no time. */
fun BoardCard.toLastChangeResponse(): BoardCardChangeResponse? {
    val change = lastChange ?: return null
    val by = lastChangedBy ?: return null
    val at = lastChangedAt ?: return null
    return BoardCardChangeResponse(change = change, by = by, at = at)
}
