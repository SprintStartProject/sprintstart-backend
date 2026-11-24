package com.sprintstart.sprintstartbackend.offboarding.scoring

/**
 * A person who could take over a file, and how well they know its surroundings.
 *
 * @property score The changes the person made to the files coupled with the file, each weighed by how
 *           many commits it shares with the file.
 */
data class Successor(
    val person: PersonKey,
    val score: Double,
)

/**
 * Suggests who to ask to review or take over a file at risk: the people who know its surroundings.
 *
 * A person's score for a file is the sum, over the files coupled with it, of their changes to that file
 * times the commits it shares with the file. People who left, who are inactive, or who already know the
 * file are never suggested.
 */
class SuccessorFinder(
    histories: List<FileHistory>,
    private val coupling: ChangeCoupling,
    private val excluded: Set<PersonKey>,
) {
    private data class Key(
        val repository: String,
        val path: String,
    )

    private val changesByFile: Map<Key, Map<PersonKey, Int>> =
        histories.associate { history ->
            Key(history.repository, history.path) to history.touches
                .filter { it.commit != null }
                .groupingBy { it.person }
                .eachCount()
        }

    /** Up to [TOP] people for [history]'s file, best first; [knowers] already know it. */
    fun suggest(history: FileHistory, knowers: Set<PersonKey>): List<Successor> =
        coupling
            .partnersOf(history)
            .flatMap(::scoresFor)
            .filter { (person, _) -> person !in excluded && person !in knowers }
            .groupBy({ it.first }, { it.second })
            .map { (person, scores) -> Successor(person, scores.sum()) }
            .sortedWith(compareByDescending<Successor> { it.score }.thenBy { it.person.value })
            .take(TOP)

    private fun scoresFor(partner: CoupledFile): List<Pair<PersonKey, Double>> =
        changesByFile[Key(partner.repository, partner.path)].orEmpty().map { (person, changes) ->
            person to changes * partner.shared.toDouble()
        }

    private companion object {
        const val TOP = 3
    }
}
