package com.sprintstart.sprintstartbackend.onboarding.service

/**
 * The team areas whose tools are mounted while one manager message is answered.
 *
 * Two things put an area here. The manager's previous message may have opened it ([carriedOver]), which is
 * what makes "yes, send it" work: drafting something opens an area, approving it comes a message later,
 * and the transcript is text only, so nothing else remembers that the area was open. And the model can open
 * one itself during this turn ([open]).
 *
 * Only what this turn opened is remembered for the next one ([openedThisTurn]). What was carried over is not
 * carried again, so an area stays open for one further message and a long conversation does not slowly
 * mount every area's tools — the reason areas exist.
 */
internal class OpenAreas(
    carriedOver: Set<TeamArea> = emptySet(),
) {
    /** Every area mounted so far, this turn's and the last one's. Read again on each hop. */
    val mounted: MutableSet<TeamArea> = carriedOver.toMutableSet()

    /** The areas the model opened during this turn. */
    val openedThisTurn: MutableSet<TeamArea> = mutableSetOf()

    fun open(area: TeamArea) {
        mounted.add(area)
        openedThisTurn.add(area)
    }
}

/** The stored form of the areas a reply opened: their names, comma-separated and sorted; null when none. */
internal fun Set<TeamArea>.encoded(): String? =
    takeIf { it.isNotEmpty() }?.sortedBy { it.name }?.joinToString(",") { it.name }

/** The areas a stored [encoded] value names. A name that is no longer an area is dropped, never an error. */
internal fun String?.toTeamAreas(): Set<TeamArea> =
    this
        ?.split(',')
        ?.mapNotNull { name -> TeamArea.entries.firstOrNull { it.name == name.trim() } }
        ?.toSet()
        .orEmpty()
