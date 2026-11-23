package com.sprintstart.sprintstartbackend.buddy.model.mapper

import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.buddy.model.response.buddy.SessionResponse

fun BuddySession.toResponse(): SessionResponse =
    SessionResponse(
        id = this.id,
        title = this.title,
        userId = this.userId,
        projectId = this.projectId,
        createdAt = this.createdAt,
    )
