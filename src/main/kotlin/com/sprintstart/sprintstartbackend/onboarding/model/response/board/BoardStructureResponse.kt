package com.sprintstart.sprintstartbackend.onboarding.model.response.board

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardStructurePayload
import java.time.Instant

/**
 * How this hire has arranged this board.
 *
 * [updatedAt] is null on a board nobody has arranged yet, which is a different fact from "arranged
 * and then emptied" and is worth being able to tell apart: the first is a new hire, the second is
 * somebody who cleared their board on purpose.
 */
data class BoardStructureResponse(
    val structure: BoardStructurePayload,
    val updatedAt: Instant?,
)
