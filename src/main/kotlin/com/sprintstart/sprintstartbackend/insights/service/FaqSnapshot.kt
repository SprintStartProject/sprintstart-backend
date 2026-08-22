package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroupRef
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqGroup
import java.util.UUID

/**
 * The entry as the AI service sees it: enough to judge a match, nothing more.
 *
 * The title falls back to the representative question for rows written before titles existed —
 * the model needs *something* to read, and the question is the honest stand-in.
 */
internal fun FaqGroup.toAiRef(): AiFaqGroupRef =
    AiFaqGroupRef(
        id = id.toString(),
        question = question,
        title = title ?: question,
        count = occurrenceCount,
    )

/**
 * Picks the entries a single classification is allowed to consider.
 *
 * The AI service holds no history, so this travels with every classification request — and it is
 * deliberately a bounded *selection* rather than everything the project ever asked. Without that
 * bound the cost of filing one question would grow with the whole question history, which is
 * exactly what the incremental path exists to avoid.
 *
 * Half the budget goes to the most-asked entries and half to the most recently asked, because the
 * two answer different halves of the question. The most-asked are what a repeat question most
 * likely belongs to; the most recent are where a *newly* opened entry lives, and those are
 * precisely the ones at risk of being duplicated — an entry created a minute ago has a count of
 * one and would never survive a purely frequency-ranked cut.
 *
 * The list is a bound, not a guarantee: a duplicate opened against a truncated candidate list is
 * folded back in by the merge pass once the ceiling is reached.
 */
internal fun candidateRefs(groups: List<FaqGroup>, limit: Int): List<AiFaqGroupRef> {
    if (limit <= 0) return emptyList()

    val byFrequency = groups.sortedByDescending { it.occurrenceCount }
    val byRecency = groups.sortedByDescending { it.lastAskedAt }

    val selected = LinkedHashMap<UUID, FaqGroup>()
    byFrequency.take((limit + 1) / 2).forEach { selected[it.id] = it }
    selected.fillFrom(byRecency, limit)
    // Anything left over goes to the next most asked, so a small project with few recent entries
    // still fills its budget instead of under-using it.
    selected.fillFrom(byFrequency, limit)

    return selected.values.map { it.toAiRef() }
}

private fun MutableMap<UUID, FaqGroup>.fillFrom(candidates: List<FaqGroup>, limit: Int) {
    for (group in candidates) {
        if (size >= limit) return
        putIfAbsent(group.id, group)
    }
}
