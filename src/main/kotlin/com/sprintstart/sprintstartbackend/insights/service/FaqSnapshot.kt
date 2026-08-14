package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqCategory
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroupRef
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqGroup
import java.util.UUID

/**
 * The group as the AI service sees it: enough to judge a match, nothing more.
 */
internal fun FaqGroup.toAiRef(): AiFaqGroupRef =
    AiFaqGroupRef(
        id = id.toString(),
        question = question,
        category = category ?: "",
        count = occurrenceCount,
    )

/**
 * The FAQ's shape as the AI service needs to see it, derived from the stored groups.
 *
 * The AI service holds no history, so this travels with every classification request. It is
 * deliberately the *structure* and not the question history: without that distinction the cost of
 * filing one question would grow with everything a project ever asked, which is exactly what the
 * incremental path exists to avoid.
 */
internal object FaqSnapshot {
    /**
     * Summarizes the project's categories with the weight of each.
     *
     * The counts matter to the model: they are how it tells an established topic from a
     * barely-used one, and so which direction a merge should go.
     */
    fun categoriesOf(groups: List<FaqGroup>): List<AiFaqCategory> =
        groups
            .mapNotNull { group -> group.category?.let { it to group } }
            .groupBy({ it.first }, { it.second })
            .map { (name, groupsInCategory) ->
                AiFaqCategory(
                    name = name,
                    groupCount = groupsInCategory.size,
                    questionCount = groupsInCategory.sumOf { it.occurrenceCount },
                )
            }.sortedByDescending { it.questionCount }

    /**
     * Picks the groups a single classification is allowed to consider.
     *
     * Half the budget goes to the most-asked groups and half to the most recently asked, because
     * the two answer different halves of the question. The most-asked are what a repeat question
     * most likely belongs to; the most recent are where a *newly* opened group lives, and those
     * are precisely the ones at risk of being duplicated — a group created a minute ago has a
     * count of one and would never survive a purely frequency-ranked cut.
     *
     * The list is a bound, not a guarantee: a duplicate opened against a truncated candidate list
     * is folded back in by the merge pass once the category fills up.
     */
    fun candidatesOf(groups: List<FaqGroup>, limit: Int): List<AiFaqGroupRef> {
        if (limit <= 0) return emptyList()

        val byFrequency = groups.sortedByDescending { it.occurrenceCount }
        val byRecency = groups.sortedByDescending { it.lastAskedAt }

        val selected = LinkedHashMap<UUID, FaqGroup>()
        byFrequency.take((limit + 1) / 2).forEach { selected[it.id] = it }
        selected.fillFrom(byRecency, limit)
        // Anything left over goes to the next most asked, so a small project with few recent
        // groups still fills its budget instead of under-using it.
        selected.fillFrom(byFrequency, limit)

        return selected.values.map { it.toAiRef() }
    }

    private fun MutableMap<UUID, FaqGroup>.fillFrom(candidates: List<FaqGroup>, limit: Int) {
        for (group in candidates) {
            if (size >= limit) return
            putIfAbsent(group.id, group)
        }
    }
}
