package com.sprintstart.sprintstartbackend.onboarding.service

private val NAMED_CALL = Regex(""""name"\s*:\s*"([\w.-]+)"""")
private val CALL_ARGUMENTS = Regex(""""(?:parameters|arguments)"\s*:""")

/**
 * Whether a model's final answer is a tool call written out as text rather than made.
 *
 * A small model sometimes answers `{"name":"find_member","parameters":{...}}` as its reply. Nothing ran,
 * and the manager reads a broken request as if it were an answer. The shape is checked, not the tool
 * name: a call to a tool that is not mounted is the same failure, and the catalogue changes.
 */
internal fun String.writesOutAToolCall(): Boolean =
    NAMED_CALL.containsMatchIn(this) && CALL_ARGUMENTS.containsMatchIn(this)

/** The tool names a reply that [writesOutAToolCall] wrote out, in the order written. */
internal fun String.writtenOutToolNames(): List<String> =
    NAMED_CALL
        .findAll(this)
        .map { it.groupValues[1] }
        .distinct()
        .toList()

/** Told to the model in place of showing the manager a call that never ran. */
internal const val TOOL_CALL_WRITTEN_OUT =
    "That reply was a tool call written out as text, so nothing ran and the manager has not seen an answer. " +
        "If you need a tool, call it properly (open its area with open_area first if it is not available). " +
        "Otherwise answer the manager in plain words."
