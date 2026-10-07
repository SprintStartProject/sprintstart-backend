package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.arrival.ArrivalStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.ArrivalStepsContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardCompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.MyCompetencyResponse

// What the live cards read as, where that is a pure mapping of something already read. Kept beside
// [BoardService] rather than inside it for the reason `PathStepCardContent.kt` is: the service
// decides which cards are on the board and reads what they need; these only say what the result
// looks like on a card.

/**
 * What is still outstanding before this hire can work, counted by how each step was settled.
 *
 * The same read the hire's own `GET /me/arrival` serves, so the card and that endpoint cannot
 * disagree — the rule every other card here follows.
 *
 * Counted per rigor and never totalled. A step the system observed and a step somebody
 * ticked are different facts, and a single blended figure here would be meaningless.
 */
internal fun arrivalStepsContent(steps: List<ResolvedArrivalStep>): ArrivalStepsContent {
    val responses: List<ArrivalStepResponse> = steps.map { it.toResponse() }

    return ArrivalStepsContent(
        steps = responses,
        observedCount = responses.count { it.rigor == Rigor.OBSERVED },
        declaredCount = responses.count { it.rigor == Rigor.DECLARED },
        outstandingCount = responses.count { !it.settled },
    )
}

internal fun MyCompetencyResponse.toBoardResponse() = BoardCompetencyResponse(
    competencyKey = competencyKey,
    label = label,
    level = level,
    targetLevel = targetLevel,
)
