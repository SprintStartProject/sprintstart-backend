package com.sprintstart.sprintstartbackend.offboarding.scoring

import kotlin.math.abs

/**
 * One file as a ranking listed it.
 *
 * @property level How the departure left the file, as the ranking named it.
 * @property score The ranking's score for the file.
 */
data class RankedFile(
    val repository: String,
    val path: String,
    val level: String,
    val score: Double,
)

/**
 * Where one file stands in two rankings. A side that does not list the file has no rank, score or level.
 *
 * @property leftRank Position in the left ranking, 1 for the most urgent.
 */
data class FileMovement(
    val repository: String,
    val path: String,
    val leftRank: Int?,
    val rightRank: Int?,
    val leftScore: Double?,
    val rightScore: Double?,
    val leftLevel: String?,
    val rightLevel: String?,
)

/**
 * How far two rankings of the same project agree.
 *
 * @property onlyLeft Files only the left ranking lists.
 * @property onlyRight Files only the right ranking lists.
 * @property both Files both rankings list.
 * @property topOverlap Share of the union of both top lists that is in both, from 0 to 1; 1 when neither
 *           lists a file.
 * @property rankCorrelation Spearman's correlation of the order of the files both list, from -1 to 1;
 *           `null` when fewer than two files are shared.
 * @property movements Every file either ranking lists, the biggest change in position first. A file only one
 *           side lists counts as moving furthest.
 */
data class MapComparison(
    val onlyLeft: Int,
    val onlyRight: Int,
    val both: Int,
    val topOverlap: Double,
    val rankCorrelation: Double?,
    val movements: List<FileMovement>,
)

/** Compares two rankings, for example one departure under two weight profiles. */
object MapComparisons {
    private const val DEFAULT_TOP = 20

    /**
     * Compares two rankings.
     *
     * @param left The ranking, most urgent first.
     * @param right The ranking to compare it with, most urgent first.
     * @param top How many of the most urgent files of each ranking count for [MapComparison.topOverlap].
     */
    fun compare(left: List<RankedFile>, right: List<RankedFile>, top: Int = DEFAULT_TOP): MapComparison {
        val leftRanks = ranksOf(left)
        val rightRanks = ranksOf(right)
        val shared = leftRanks.keys.filter { it in rightRanks }
        return MapComparison(
            onlyLeft = leftRanks.size - shared.size,
            onlyRight = rightRanks.size - shared.size,
            both = shared.size,
            topOverlap = overlap(left.take(top).map(::keyOf).toSet(), right.take(top).map(::keyOf).toSet()),
            rankCorrelation = correlation(shared, leftRanks, rightRanks),
            movements = movements(left, right),
        )
    }

    private fun keyOf(file: RankedFile) = file.repository to file.path

    private fun ranksOf(files: List<RankedFile>): Map<Pair<String, String>, Int> =
        files.withIndex().associate { (index, file) -> keyOf(file) to index + 1 }

    private fun overlap(left: Set<Pair<String, String>>, right: Set<Pair<String, String>>): Double {
        val union = left + right
        return if (union.isEmpty()) 1.0 else (left intersect right).size.toDouble() / union.size
    }

    /** Spearman's coefficient over the shared files, each side re-ranked among them. */
    private fun correlation(
        shared: List<Pair<String, String>>,
        leftRanks: Map<Pair<String, String>, Int>,
        rightRanks: Map<Pair<String, String>, Int>,
    ): Double? {
        if (shared.size < 2) return null
        val amongLeft = shared.sortedBy { leftRanks.getValue(it) }.withIndex().associate { it.value to it.index + 1 }
        val amongRight = shared.sortedBy { rightRanks.getValue(it) }.withIndex().associate { it.value to it.index + 1 }
        val squares = shared.sumOf { file ->
            val difference = (amongLeft.getValue(file) - amongRight.getValue(file)).toDouble()
            difference * difference
        }
        val count = shared.size.toDouble()
        return 1 - 6 * squares / (count * (count * count - 1))
    }

    private fun movements(left: List<RankedFile>, right: List<RankedFile>): List<FileMovement> {
        val leftByKey = left.withIndex().associate { keyOf(it.value) to it }
        val rightByKey = right.withIndex().associate { keyOf(it.value) to it }
        return (leftByKey.keys + rightByKey.keys)
            .map { key ->
                val onLeft = leftByKey[key]
                val onRight = rightByKey[key]
                FileMovement(
                    repository = key.first,
                    path = key.second,
                    leftRank = onLeft?.let { it.index + 1 },
                    rightRank = onRight?.let { it.index + 1 },
                    leftScore = onLeft?.value?.score,
                    rightScore = onRight?.value?.score,
                    leftLevel = onLeft?.value?.level,
                    rightLevel = onRight?.value?.level,
                )
            }.sortedWith(
                compareByDescending<FileMovement> { distanceOf(it) }.thenBy { it.repository }.thenBy { it.path },
            )
    }

    private fun distanceOf(movement: FileMovement): Int =
        if (movement.leftRank == null || movement.rightRank == null) {
            Int.MAX_VALUE
        } else {
            abs(movement.leftRank - movement.rightRank)
        }
}
