package com.sprintstart.sprintstartbackend.offboarding.scoring

import java.util.UUID

/**
 * A file that tends to change in the same commits as another.
 *
 * @property degree The shared commits as a fraction of the two files' average number of commits, from 0 to 1.
 */
data class CoupledFile(
    val repository: String,
    val path: String,
    val degree: Double,
)

/**
 * How strongly a file is tied to others by changing with them.
 *
 * @property sum The degrees of every coupled file added up; a file that changes with many others scores high.
 * @property partners The most strongly coupled files, strongest first.
 */
data class Coupling(
    val sum: Double,
    val partners: List<CoupledFile>,
) {
    companion object {
        /** A file that changes alone. */
        val NONE = Coupling(0.0, emptyList())
    }
}

/**
 * Finds files that change together from the commits that changed them, without reading any code.
 *
 * Risk spreads along such ties: whoever changes a file that is hard to understand alone must also
 * understand the file that always changes with it. Only pairs that changed together at least
 * [minShared] times and with a degree of at least [minDegree] count, because few commits make a
 * coincidence look like a tie.
 */
class ChangeCoupling(
    histories: List<FileHistory>,
    private val minShared: Int = MIN_SHARED,
    private val minDegree: Double = MIN_DEGREE,
) {
    private data class Key(
        val repository: String,
        val path: String,
    )

    private val couplings: Map<Key, Coupling>

    init {
        val commitsByFile = histories.associate { Key(it.repository, it.path) to commitsOf(it) }
        couplings = partnersByFile(commitsByFile).mapValues { (_, partners) -> coupling(partners) }
    }

    /** The coupling of [history]'s file. */
    fun of(history: FileHistory): Coupling = couplings[Key(history.repository, history.path)] ?: Coupling.NONE

    private fun commitsOf(history: FileHistory): Set<UUID> = history.touches.mapNotNull { it.commit }.toSet()

    private fun partnersByFile(commitsByFile: Map<Key, Set<UUID>>): Map<Key, List<CoupledFile>> =
        sharedCommits(commitsByFile)
            .filterValues { it >= minShared }
            .flatMap { (pair, together) ->
                val degree =
                    together /
                        ((commitsByFile.getValue(pair.first).size + commitsByFile.getValue(pair.second).size) / 2.0)
                if (degree < minDegree) emptyList() else both(pair, degree)
            }.groupBy({ it.first }, { it.second })

    private fun both(pair: Pair<Key, Key>, degree: Double): List<Pair<Key, CoupledFile>> =
        listOf(
            pair.first to CoupledFile(pair.second.repository, pair.second.path, degree),
            pair.second to CoupledFile(pair.first.repository, pair.first.path, degree),
        )

    private fun sharedCommits(commitsByFile: Map<Key, Set<UUID>>): Map<Pair<Key, Key>, Int> {
        val filesByCommit = commitsByFile.entries
            .flatMap { (file, commits) -> commits.map { it to file } }
            .groupBy({ it.first }, { it.second })
        return filesByCommit.values
            .flatMap { files -> pairsOf(files.sortedWith(compareBy({ it.repository }, { it.path }))) }
            .groupingBy { it }
            .eachCount()
    }

    private fun pairsOf(files: List<Key>): List<Pair<Key, Key>> =
        files.flatMapIndexed { index, first -> files.drop(index + 1).map { first to it } }

    private fun coupling(partners: List<CoupledFile>) =
        Coupling(
            sum = partners.sumOf { it.degree },
            partners = partners.sortedWith(compareByDescending<CoupledFile> { it.degree }.thenBy { it.path }).take(TOP),
        )

    private companion object {
        const val MIN_SHARED = 3
        const val MIN_DEGREE = 0.3
        const val TOP = 3
    }
}
