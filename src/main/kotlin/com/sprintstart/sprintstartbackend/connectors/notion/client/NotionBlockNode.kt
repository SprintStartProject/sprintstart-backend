package com.sprintstart.sprintstartbackend.connectors.notion.client

data class NotionBlockNode(
    val block: NotionBlockResponse,
    val children: List<NotionBlockNode> = emptyList(),
)
