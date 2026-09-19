package com.sprintstart.sprintstartbackend.connectors.notion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotionTableRow(
    @SerialName("cells")
    val cells : List<List<NotionRichText>>,
)
