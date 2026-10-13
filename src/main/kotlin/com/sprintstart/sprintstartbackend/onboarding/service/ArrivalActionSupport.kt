package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ArrivalDerivation
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import java.util.UUID

/*
 * The actions of team mode's arrival area.
 *
 * Everything here writes with `context.projectId`, never the organisation-wide default list (a null
 * project). That list is read only to keep previews honest: a project step wins its key outright, so
 * creating one replaces the company step of that key for everybody here, and deleting one lets it back.
 *
 * Keys are the identity, not ids, and the service will not rename one, since state is recorded against
 * the key. Renaming is therefore delete plus create, and the tools say so.
 */

/** The step [key] names on [projectId], or null. */
internal fun ArrivalStepService.stepOn(key: String, projectId: UUID): ArrivalStep? =
    listForAuthoring(projectId).firstOrNull { it.key == key }

/** A preview line for a field that can be replaced or, as an empty [to], taken off. */
internal fun fieldLine(label: String, from: String?, to: String, show: (String) -> String): String =
    if (to.isEmpty()) {
        "$label: ${show(from.orEmpty())} is taken off, leaving none"
    } else {
        "$label: ${from?.let(show) ?: "(none)"} becomes ${show(to)}"
    }

/** The step other than [key] already sitting on [position], or null when nobody is. */
internal fun occupantOf(position: Int, key: String, steps: List<ArrivalStep>): ArrivalStep? =
    steps.firstOrNull { it.position == position && it.key != key }

/** A model-supplied key, lower-cased and trimmed the way the service will store it. */
internal fun normalizeKey(raw: String): String = raw.trim().lowercase()

/** What a step with this key will actually be settled by: a key the system checks is forced to [Rigor.OBSERVED]. */
internal fun effectiveRigor(key: String, requested: Rigor): Rigor =
    if (ArrivalDerivation.forStepKey(key) != null) Rigor.OBSERVED else requested

internal fun rigorOrNull(raw: String): Rigor? =
    Rigor.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }

internal fun settlementWords(rigor: Rigor): String =
    when (rigor) {
        Rigor.OBSERVED -> "the system observing it"
        Rigor.ATTESTED -> "somebody else attesting it"
        Rigor.DECLARED -> "the hire declaring it"
    }

internal const val NO_SUCH_STEP =
    "This project has no arrival step with that key. Call list_arrival_steps for the keys it does have."

internal const val STEP_GONE_SINCE = "That arrival step was changed or removed since, so nothing was changed."

internal const val RIGOR_VALUES = "Must be one of observed, attested or declared."
