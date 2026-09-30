package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCard
import java.util.UUID

/**
 * Which cards a new order actually moved.
 *
 * Pure, and kept out of [BoardService] on purpose: it reads no repository and holds no state, so it
 * is the part of rearranging a board that can be understood without the rest of that class.
 */
internal object BoardCardOrder {
    /**
     * The cards in [newOrder] whose order relative to the others changed.
     *
     * Everything outside the longest run that is still in its old relative order: the smallest set of
     * cards that, picked up and put back, turns the old order into the new one — which is what a
     * person means by the cards that were moved. Ties in the old positions break by id, so the answer
     * does not depend on how the rows came back from the database.
     */
    fun moved(newOrder: List<BoardCard>): Set<UUID> {
        val oldRank = newOrder
            .sortedWith(compareBy<BoardCard> { it.position }.thenBy { it.id })
            .withIndex()
            .associate { (rank, card) -> card.id to rank }
        val kept = longestIncreasingRun(newOrder.map { oldRank.getValue(it.id) })
        return newOrder.indices
            .filterNot { it in kept }
            .map { newOrder[it].id }
            .toSet()
    }

    /** The indices of one longest strictly increasing subsequence of [values], patience-sorted. */
    private fun longestIncreasingRun(values: List<Int>): Set<Int> {
        val tails = mutableListOf<Int>() // index into values of the smallest tail of each length
        val previous = IntArray(values.size) { -1 }
        values.forEachIndexed { i, value ->
            var low = 0
            var high = tails.size
            while (low < high) {
                val mid = (low + high) / 2
                if (values[tails[mid]] < value) low = mid + 1 else high = mid
            }
            if (low > 0) previous[i] = tails[low - 1]
            if (low == tails.size) tails.add(i) else tails[low] = i
        }
        return generateSequence(tails.lastOrNull()) { previous[it].takeIf { p -> p >= 0 } }.toSet()
    }
}
