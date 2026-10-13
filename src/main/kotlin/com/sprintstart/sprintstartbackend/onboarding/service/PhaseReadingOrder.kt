package com.sprintstart.sprintstartbackend.onboarding.service

import java.util.UUID

/**
 * The order a phase's items read in on the hire's page: row by row of the phase's dependency
 * graph, and inside a row in the order the graph draws them.
 *
 * A port of `arrangeRows` in the frontend's `features/onboarding/graph/layout.ts`, and it has to stay
 * one: the page's "up next" is the first open item in this order, and the buddy names the next thing
 * from the same order, so the two can only agree while the rule is the same on both sides. Change one
 * and change the other, including the tie-breaks.
 *
 * - A node's **row** is one below the deepest thing it waits on. A cycle -- which the backend refuses,
 *   but data can still hold one -- is cut where it is found.
 * - Inside a row, a few passes of the **barycentre** heuristic pull every node towards the average
 *   column of what it connects to in the row above, then below. The input order breaks ties, so a
 *   graph with no edges reads in the order it came in.
 */
internal object PhaseReadingOrder {
    private const val PASSES = 4

    /**
     * Orders [nodes] -- ids with what each waits on, in input order -- the way the page reads them.
     *
     * The input order is the page's: steps by position, then questions by position.
     */
    fun of(nodes: List<Pair<UUID, Set<UUID>>>): List<UUID> {
        if (nodes.isEmpty()) return emptyList()
        val ids = nodes.map { it.first }
        val inGraph = ids.toSet()
        val blockers = nodes.associate { (id, waitsOn) -> id to waitsOn.filter { it in inGraph && it != id } }
        val dependents = ids.associateWith { mutableListOf<UUID>() }
        blockers.forEach { (id, waitsOn) -> waitsOn.forEach { dependents[it]?.add(id) } }

        val ranks = ranks(ids, blockers)
        val rows = List(ranks.values.max() + 1) { mutableListOf<UUID>() }
        ids.forEach { rows[ranks.getValue(it)].add(it) }

        val inputIndex = ids.withIndex().associate { it.value to it.index }
        val column = HashMap<UUID, Int>()
        val indexRow = { row: List<UUID> -> row.forEachIndexed { index, id -> column[id] = index } }
        rows.forEach(indexRow)

        val sortRow = { row: MutableList<UUID>, neighboursOf: (UUID) -> List<UUID> ->
            val weight = row.associateWith { id ->
                val neighbours = neighboursOf(id).filter { it in column }
                if (neighbours.isEmpty()) {
                    column.getValue(id).toDouble()
                } else {
                    neighbours.sumOf { column.getValue(it).toDouble() } / neighbours.size
                }
            }
            row.sortWith(compareBy<UUID> { weight.getValue(it) }.thenBy { inputIndex.getValue(it) })
            indexRow(row)
        }

        repeat(PASSES) {
            for (index in 1 until rows.size) sortRow(rows[index]) { blockers[it].orEmpty() }
            for (index in rows.size - 2 downTo 0) sortRow(rows[index]) { dependents[it].orEmpty() }
        }
        return rows.flatten()
    }

    private fun ranks(ids: List<UUID>, blockers: Map<UUID, List<UUID>>): Map<UUID, Int> {
        val ranks = HashMap<UUID, Int>()
        val visiting = HashSet<UUID>()

        fun rankOf(id: UUID): Int {
            ranks[id]?.let { return it }
            if (id in visiting) return 0
            visiting += id
            val rank = (blockers[id].orEmpty().maxOfOrNull { rankOf(it) } ?: -1) + 1
            visiting -= id
            ranks[id] = rank
            return rank
        }

        ids.forEach { rankOf(it) }
        return ranks
    }
}
