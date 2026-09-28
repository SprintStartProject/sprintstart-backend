package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyTeamMessage

/**
 * The team areas whose tools are mounted while one manager message is answered.
 *
 * Two things put an area here. Earlier in the visit a reply may have opened it ([carriedOver]), which is
 * what makes "yes, send it" work however long the discussion before it ran: drafting something opens an
 * area, and approving it comes messages later, and the transcript is text only, so nothing else remembers
 * that the area was open. And the model can open one itself during this turn ([open]).
 *
 * Only what this turn opened is stored with its reply ([openedThisTurn]); the visit's set is read back
 * from those ([areasOpenedThisVisit]), so nothing inherited is stored twice.
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

/**
 * The areas any reply of the current visit opened, for the transcript read oldest first.
 *
 * A visit begins at the last greeting, the same boundary the manager sees, so a new visit starts with
 * nothing mounted. Within one, an area stays open: an area is only ever opened because the manager asked
 * about something in it, so what accumulates is what they have actually been working on.
 */
internal fun List<BuddyTeamMessage>.areasOpenedThisVisit(): Set<TeamArea> =
    drop(indexOfLast { it.opening }.coerceAtLeast(0))
        .filter { it.role == BuddyMessageRole.ASSISTANT }
        .flatMap { it.openedAreas.toTeamAreas() }
        .toSet()

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
