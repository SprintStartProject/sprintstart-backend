package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.TaskType

/**
 * Ranks the starter-work pool for one hire on signals that actually predict a good first task.
 *
 * **Deterministic and pure, on purpose.** ⚠️ Each signal contributes both points *and* the
 * sentence justifying them, and the same inputs always give the same ranking. A hire must be told
 * in one line why a task was suggested — a suggestion you cannot interrogate is an instruction —
 * and an embedding distance cannot explain itself, so an explanation reconstructed from a hidden
 * score is a rationalisation.
 *
 * The signal set follows R7 (first issues a newcomer resolves cluster by task type, language and
 * domain; expertise preference and prior experience predict what they pick up), plus one row that
 * does not come from the literature but from R1: **an unblocked task owned by people who never
 * answer is not an unblocked task.** Responsiveness therefore *demotes*, never hides — a stale
 * repository is a finding for a PM, not a reason to bury real work from a hire.
 */
object StarterWorkMatcher {
    /** Competency overlap: still the strongest single signal, no longer the only one. */
    private const val COMPETENCY_WEIGHT = 40.0

    /** Doing again the kind of work you have done before (R7's "task type"). */
    private const val TASK_TYPE_WEIGHT = 20.0

    /** Working where you have worked before (R7's "prior experience"). */
    private const val REPO_FAMILIARITY_WEIGHT = 20.0

    /** A task nobody has to teach you the surrounding domain for. */
    private const val LABEL_FAMILIARITY_WEIGHT = 10.0

    /**
     * With no history at all, low-risk task types get a small nudge rather than nothing — a first
     * contribution that is a doc fix succeeds more often than one that is a refactor. Deliberately
     * smaller than any real signal: it is a default, not a belief about the person.
     */
    private const val NEWCOMER_SAFE_TYPE_BONUS = 8.0

    /**
     * A task nobody has looked at yet loses this much.
     *
     * Review stopped being a gate: a mined task is claimable the moment it is mined, so this is the
     * only thing left expressing "somebody has vouched for this one". It is deliberately **smaller
     * than the smallest positive signal** ([NEWCOMER_SAFE_TYPE_BONUS]) — an unreviewed task that
     * fits a hire perfectly must still beat a reviewed one that does not, or the demotion has
     * quietly reimplemented the approval requirement it replaced.
     */
    private const val UNREVIEWED_PENALTY = 5.0

    /** Repositories that answer slowly lose up to this much. Capped so it can demote, never bury. */
    private const val RESPONSIVENESS_MAX_PENALTY = 15.0

    /** Beyond this, waiting longer is not meaningfully worse — it is already too long. */
    private const val SLOW_RESPONSE_HOURS = 72.0

    /** Task types a newcomer with no track record is nudged toward. */
    private val SAFE_FIRST_TYPES = setOf(TaskType.DOCS, TaskType.TEST)

    /**
     * What is known about one hire when ranking. All of it is already-held data — nothing here
     * triggers a fetch, and an absent signal is treated as *unknown*, never as evidence of
     * inexperience.
     */
    data class HireProfile(
        /** Competency keys held at level > 0. */
        val competencyKeys: Set<String>,
        /** `owner/name` repositories the hire has authored issues or pull requests in. */
        val familiarRepositories: Set<String>,
        /** Issue labels the hire's prior work carried, lower-cased. */
        val familiarLabels: Set<String>,
        /** Task types the hire has worked on before, derived from those labels. */
        val familiarTaskTypes: Set<TaskType>,
    ) {
        /**
         * True when nothing is known about this person's prior work. Consent may simply not have
         * been given — so this must never be read as "beginner", only as "no evidence".
         */
        val hasNoHistory: Boolean
            get() = familiarRepositories.isEmpty() && familiarLabels.isEmpty()
    }

    /** One pool task reduced to what ranking looks at. */
    data class TaskFeatures(
        val competencyKeys: Set<String>,
        val taskType: TaskType,
        val labels: Set<String>,
        val repositoryFullName: String?,
        /** Whether a person has looked at this task. Unreviewed is claimable, just ranked lower. */
        val reviewed: Boolean = true,
    )

    /** A repository's answering behaviour, mirroring the ingestion module's `RepositoryResponsiveness`. */
    data class Responsiveness(
        val medianHoursToFirstResponse: Long?,
        val unansweredCount: Int,
    )

    data class Score(
        val score: Double,
        val matchedCompetencyKeys: List<String>,
        /** Why this task was suggested, one clause per contributing signal, strongest first. */
        val reasons: List<String>,
    )

    /**
     * Scores one task for one hire.
     *
     * @param profile What is known about the hire.
     * @param task The task's features.
     * @param responsiveness The task repository's answering behaviour, or null when unknown.
     */
    @Suppress("CyclomaticComplexMethod")
    fun score(
        profile: HireProfile,
        task: TaskFeatures,
        responsiveness: Responsiveness?,
    ): Score {
        val reasons = mutableListOf<Pair<Double, String>>()

        val matched = task.competencyKeys.intersect(profile.competencyKeys).sorted()
        if (task.competencyKeys.isNotEmpty() && matched.isNotEmpty()) {
            val share = matched.size.toDouble() / task.competencyKeys.size
            reasons += COMPETENCY_WEIGHT * share to
                "uses ${matched.joinToString(", ")}, which you have already shown"
        }

        if (task.taskType in profile.familiarTaskTypes) {
            reasons += TASK_TYPE_WEIGHT to "is a ${task.taskType.label} task, the kind you have worked on before"
        } else if (profile.hasNoHistory && task.taskType in SAFE_FIRST_TYPES) {
            reasons += NEWCOMER_SAFE_TYPE_BONUS to
                "is a ${task.taskType.label} task, a forgiving place to make a first change here"
        }

        if (task.repositoryFullName != null && task.repositoryFullName in profile.familiarRepositories) {
            reasons += REPO_FAMILIARITY_WEIGHT to "is in ${task.repositoryFullName}, where you have worked before"
        }

        val sharedLabels = task.labels.intersect(profile.familiarLabels).sorted()
        if (sharedLabels.isNotEmpty()) {
            reasons += LABEL_FAMILIARITY_WEIGHT to
                "is labelled ${sharedLabels.joinToString(", ")}, like work you have done before"
        }

        val penalty = responsivenessPenalty(responsiveness)
        // Every signal owes a sentence, demotions included -- a score a hire cannot read is a
        // number standing in for a reason.
        val unreviewed = if (task.reviewed) {
            null
        } else {
            "note: nobody has reviewed this task yet"
        }

        val total = reasons.sumOf { it.first } - penalty.first - if (task.reviewed) 0.0 else UNREVIEWED_PENALTY

        val ordered = reasons.sortedByDescending { it.first }.map { it.second }.toMutableList()
        penalty.second?.let { ordered += it }
        unreviewed?.let { ordered += it }

        return Score(score = total, matchedCompetencyKeys = matched, reasons = ordered)
    }

    /**
     * How much a repository's slowness costs a task, and how to say so.
     *
     * Never a filter: the penalty is capped well below what a single positive signal is worth, so
     * a strongly-matched task in a slow repository still outranks a weakly-matched one in a fast
     * one. The hire is told, because "you may wait for a review here" is exactly the thing a
     * newcomer otherwise reads as their own failure.
     */
    private fun responsivenessPenalty(responsiveness: Responsiveness?): Pair<Double, String?> {
        if (responsiveness == null) return 0.0 to null

        val median = responsiveness.medianHoursToFirstResponse
        if (median == null) {
            // Not "no data": pull requests here exist and none of them has ever been answered.
            return if (responsiveness.unansweredCount > 0) {
                RESPONSIVENESS_MAX_PENALTY to "note: pull requests here have not been getting responses"
            } else {
                0.0 to null
            }
        }

        if (median < SLOW_RESPONSE_HOURS / 2) return 0.0 to null

        val severity = (median / SLOW_RESPONSE_HOURS).coerceAtMost(1.0)
        return RESPONSIVENESS_MAX_PENALTY * severity to
            "note: reviews here take about $median hours to arrive"
    }
}
