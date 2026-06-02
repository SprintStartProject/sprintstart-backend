package com.sprintstart.sprintstartbackend.upload.external.events

import java.io.Serializable

data class AiIngestResponse(
    val success: Boolean,
) : Serializable