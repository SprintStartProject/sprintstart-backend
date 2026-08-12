package com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CandidatePoolState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.TaskType
import java.time.Instant
import java.util.UUID

data class StarterWorkTaskProposalResponse(
    val id: UUID,
    val sourceId: String,
    val title: String,
    val summary: String?,
    val rationale: String?,
    val sourceUrl: String?,
    val competencyKeys: List<String>,
    val status: ProposalStatus,
    /** True when a PM has flagged this approved task as suitable for Task 0. */
    val taskZeroEligible: Boolean,
    /** Which track this work is for, or null when it suits any role. */
    val onboardingTrackKey: String? = null,
)

/**
 * The live starter-work tasks nobody has vouched for yet.
 *
 * ⚠️ **"Unreviewed" is not "awaiting review".** These tasks are claimable right now; reviewing one
 * lifts a fit-ranking demotion rather than admitting it to anything.
 */
data class UnreviewedStarterWorkResponse(
    val tasks: List<StarterWorkTaskProposalResponse>,
)

/**
 * One open corpus issue somebody could put into the starter-work pool by hand.
 *
 * ⚠️ **Not a proposal and not a ranking.** Nothing has judged this issue — it is simply an issue the
 * project has ingested that is still open. There is deliberately no score and no suitability field:
 * the whole point of browsing is that the judgement is the reader's, and a number here would be the
 * mining filter wearing a different hat.
 *
 * [hasAssignee] is three-valued and **null means *we do not know***, not "nobody" — this system does
 * not ingest GitHub assignees, so every GitHub issue reports null. A client must render that as
 * unknown; treating it as free is the "absent history is not beginner" defect in another place.
 *
 * [excerpt] is the issue's own text, cut to a length a list can show. [excerptTruncated] says when
 * something was cut, next to the thing it limits: a reader shown a partial body and not told it is
 * partial reads the absence as the whole story.
 */
data class StarterWorkCandidateResponse(
    val sourceId: String,
    /** Which tracker it came from — `GITHUB`, `JIRA`. */
    val tracker: String,
    val title: String,
    val excerpt: String?,
    val excerptTruncated: Boolean,
    val labels: List<String>,
    val sourceUrl: String?,
    val hasAssignee: Boolean?,
    val poolState: CandidatePoolState,
    /** When the issue last changed at its source; null when the tracker never said. */
    val updatedAtSource: Instant?,
)

data class GenerateStarterWorkResponse(
    val status: String,
    val tasksProposed: Int,
    val notes: List<String>,
)

/**
 * One pool task ranked for one hire.
 *
 * [reasons] is not decoration: matching is a *suggestion*, and a suggestion nobody can interrogate
 * is an instruction. Each entry is one clause the client can render as "suggested because it …",
 * strongest signal first, with any responsiveness warning last. An empty list means nothing matched
 * and the task is only in the list because it is available — which is worth saying plainly rather
 * than dressing up with an invented reason.
 */
data class RankedStarterWorkTaskResponse(
    val task: StarterWorkTaskProposalResponse,
    val score: Double,
    val matchedCompetencyKeys: List<String>,
    val taskType: TaskType,
    val reasons: List<String>,
)
