package com.sprintstart.sprintstartbackend.assistant.external

/**
 * How many backend-tool round trips a turn may take before it answers with what it has; both assistants'
 * loops share the budget.
 */
const val MAX_AGENT_STEPS = 5

/**
 * The most messages the AI is ever sent; older turns reach it only through the running summary.
 * Compaction folds back down to the same number, so the prompt stays within budget.
 */
const val CONVERSATION_WINDOW = 20
