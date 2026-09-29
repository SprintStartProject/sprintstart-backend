package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyTeamMessage

/**
 * How many of the latest replies of a visit keep open an area they opened or used.
 *
 * Enough for "make it shorter", "friendlier please", "ok, send it" after a draft; small enough that a
 * conversation moving through several topics does not end up with every area's tools mounted at once.
 */
internal const val AREA_OPEN_FOR_REPLIES = 3

/**
 * The team areas whose tools are mounted while one manager message is answered.
 *
 * Two things put an area here. One of the latest replies may have opened or used it ([carriedOver]),
 * which is what makes "yes, send it" work after a short discussion: drafting something opens an area,
 * approving it comes a message or two later, and the transcript is text only, so nothing else remembers
 * that the area was open. And the area can be opened during this turn ([open]), by `open_area` or by the
 * model calling one of its tools before it was mounted.
 *
 * What this turn opened or used is stored with its reply ([activeThisTurn]), and the next turn reads the
 * latest replies' back ([areasStillOpen]). An area in use keeps being stored, so it stays open; one left
 * alone closes after [AREA_OPEN_FOR_REPLIES] replies.
 */
internal class OpenAreas(
    carriedOver: Set<TeamArea> = emptySet(),
) {
    /** Every area mounted so far, this turn's and the recent replies'. Read again on each hop. */
    val mounted: MutableSet<TeamArea> = carriedOver.toMutableSet()

    /** The areas opened or used during this turn. */
    val activeThisTurn: MutableSet<TeamArea> = mutableSetOf()

    fun open(area: TeamArea) {
        mounted.add(area)
        activeThisTurn.add(area)
    }

    /** Records that a tool of a mounted [area] ran, so the area stays open for the next replies. */
    fun use(area: TeamArea) {
        if (area in mounted) {
            activeThisTurn.add(area)
        }
    }
}

/**
 * The areas the latest [AREA_OPEN_FOR_REPLIES] replies of the current visit opened or used, for the
 * transcript read oldest first.
 *
 * A visit begins at the last greeting, the same boundary the manager sees, so a new visit starts with
 * nothing mounted. The greeting itself is not a reply to anything and does not count towards the window.
 */
internal fun List<BuddyTeamMessage>.areasStillOpen(): Set<TeamArea> =
    drop(indexOfLast { it.opening }.coerceAtLeast(0))
        .filter { it.role == BuddyMessageRole.ASSISTANT && !it.opening }
        .takeLast(AREA_OPEN_FOR_REPLIES)
        .flatMap { it.openedAreas.toTeamAreas() }
        .toSet()

/** The stored form of the areas a reply opened or used: names, comma-separated and sorted; null when none. */
internal fun Set<TeamArea>.encoded(): String? =
    takeIf { it.isNotEmpty() }?.sortedBy { it.name }?.joinToString(",") { it.name }

/** The areas a stored [encoded] value names. A name that is no longer an area is dropped, never an error. */
internal fun String?.toTeamAreas(): Set<TeamArea> =
    this
        ?.split(',')
        ?.mapNotNull { name -> TeamArea.entries.firstOrNull { it.name == name.trim() } }
        ?.toSet()
        .orEmpty()
