package com.sprintstart.sprintstartbackend.connectors.notion

data class ParsedNotionBody(
    val bodyText: String,
    val sections: List<ParsedNotionSection> = emptyList(),
    val tables: List<String> = emptyList(),
    val codeBlocks: List<ParsedNotionCodeBlock> = emptyList(),
)

data class ParsedNotionSection(
    val heading: String,
    val level: Int,
)

data class ParsedNotionCodeBlock(
    val language: String?,
    val code: String,
)
