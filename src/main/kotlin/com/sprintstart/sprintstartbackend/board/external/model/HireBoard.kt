package com.sprintstart.sprintstartbackend.board.external.model

import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId

/** Whose board: a hire has one board per project they are on. */
data class HireBoard(
    val userId: UserId,
    val projectId: ProjectId,
)
