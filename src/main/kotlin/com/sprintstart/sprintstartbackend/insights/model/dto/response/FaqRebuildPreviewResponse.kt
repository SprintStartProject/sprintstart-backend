package com.sprintstart.sprintstartbackend.insights.model.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "How much material a manual FAQ rebuild would have, per requested time window. " +
        "Lets a client show what each scope would cover before one is chosen, since a rebuild " +
        "replaces the FAQ and drops whatever it does not cover.",
)
data class FaqRebuildPreviewResponse(
    @field:Schema(description = "Questions asked in this project's chats, over all time.")
    val totalQuestionCount: Int,
    @field:Schema(description = "The cap on how many questions a rebuild may send, whatever scope is chosen.")
    val rebuildQuestionLimit: Int,
    @field:Schema(description = "One entry per requested window, in the order they were requested.")
    val windows: List<FaqRebuildWindowResponse>,
)

@Schema(description = "How many questions fall within one time window.")
data class FaqRebuildWindowResponse(
    @field:Schema(description = "The window's length in days, as requested.")
    val sinceDays: Int,
    @field:Schema(
        description = "Questions a rebuild scoped to this window would send. Already capped, so " +
            "it is what would actually happen rather than what exists.",
    )
    val questionCount: Int,
)
