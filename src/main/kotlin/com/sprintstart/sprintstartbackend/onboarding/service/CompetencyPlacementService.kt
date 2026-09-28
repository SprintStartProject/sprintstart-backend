package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProficiencyLevel
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Placing a hire on the competencies nobody has evidence for yet, from the conversation.
 *
 * The mentor offers a short assessment, runs it in the thread, and — on the hire's confirmation —
 * records where it put them. This service owns both halves of that: which competencies are still
 * unplaced ([topicsFor]), and the ledger write ([record]).
 *
 * ### What a placement is worth
 *
 * A weak prior, and the code says so rather than the prose. Every entry written here is
 * [CompetencySource.ASSESSED], and a row already carrying [CompetencySource.VERIFIED] is left
 * untouched: accepted work outranks anything somebody said about themselves, never the other way
 * round. That is the same rule [RampService] states from the other side, enforced here so a
 * conversation cannot walk a proven competency back.
 *
 * Otherwise the write is the monotonic find-or-create every ledger writer uses — a placement can
 * raise a level, never lower one. A hire who undersells themselves in chat loses nothing they had
 * already shown.
 *
 * ### Why unplaced means level 0 too
 *
 * A level-0 row is *placed-but-unknown*, which is the ledger recording that nobody knows — not
 * evidence of anything. It is the same reading `MyCompetencyService`'s clients and
 * [StarterWorkTaskProposalService]'s profile take, so a competency sitting at 0 is still worth
 * asking about.
 */
@Service
class CompetencyPlacementService(
    private val competencyRepository: CompetencyRepository,
    private val userCompetencyStateRepository: UserCompetencyStateRepository,
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val userApi: UserApi,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * The competencies worth asking this hire about, most useful first.
     *
     * "Worth asking about" is *no evidence either way*: no ledger row, or one at level 0. Anything
     * the hire has already shown is left alone — an assessment that re-asks about a competency
     * their merged work proved wastes the one thing a short conversation has.
     *
     * Ordered by whether the work waiting for them needs it. A competency the live starter-work
     * pool exercises decides what they can pick up this week; one nothing in the pool touches
     * decides nothing yet. Within each group the order is by label, so the list is stable across
     * calls rather than at the mercy of row order.
     *
     * Capped at [MAX_TOPICS]. A catalogue generated from a large codebase runs to dozens of
     * competencies and a *short* assessment is the whole design — handing the mentor all of them
     * invites an interrogation.
     */
    @Transactional
    fun topicsFor(userId: UUID): List<PlacementTopic> {
        adoptDeclaredSkills(userId)

        val placed = userCompetencyStateRepository
            .findAllByUserId(userId)
            .filter { it.level > 0 }
            .map { it.competencyKey }
            .toSet()

        val unplaced = competencyRepository.findAll().filter { it.key !in placed }
        if (unplaced.isEmpty()) return emptyList()

        val wanted = keysTheLivePoolNeeds()
        return unplaced
            .sortedWith(compareByDescending<Competency> { it.key in wanted }.thenBy { it.label })
            .take(MAX_TOPICS)
            .map {
                PlacementTopic(
                    key = it.key,
                    label = it.label,
                    description = it.description,
                    kind = it.kind.name.lowercase(),
                    neededByAvailableWork = it.key in wanted,
                )
            }
    }

    /**
     * Carries what a user already said about themselves into the ledger, once.
     *
     * A user states their skills when they join. Those are the same claims an assessment would
     * collect, so a hire who has already answered is not asked again — the entries land as
     * [CompetencySource.DECLARED] and [topicsFor] then passes over them like any other placed
     * competency.
     *
     * Matched on the name, normalised, against a competency's key or its label: both are the words
     * a person would type, and a skill named "Kotlin" and a competency keyed `kotlin` are the same
     * thing. A skill matching nothing in the catalogue is left alone rather than invented as a
     * competency — the graph is the team's, not the user's, to extend.
     *
     * Runs on read, like every other lazy write here, and only ever creates: an existing row of any
     * source is left exactly as it is, so this can never walk back a level the user has since
     * shown, and running twice records once.
     */
    private fun adoptDeclaredSkills(userId: UUID) {
        val declared = userApi.getDeclaredSkills(userId)
        if (declared.isEmpty()) return

        val alreadyInLedger = userCompetencyStateRepository
            .findAllByUserId(userId)
            .map { it.competencyKey }
            .toSet()

        val byName = competencyRepository
            .findAll()
            .flatMap { competency -> listOf(competency.key, competency.label).map { normalise(it) to competency } }
            .toMap()

        declared.forEach { skill ->
            val competency = byName[normalise(skill.name)] ?: return@forEach
            if (competency.key in alreadyInLedger) return@forEach
            // Both scales are named beginner..expert, so they are matched by name. Reading the
            // position instead would go quietly wrong the day either enum is reordered.
            val level = ProficiencyLevel.fromWord(skill.level.name) ?: return@forEach
            userCompetencyStateRepository.save(
                UserCompetencyState(
                    userId = userId,
                    competencyKey = competency.key,
                    level = level.rank,
                    source = CompetencySource.DECLARED,
                ),
            )
        }
    }

    private fun normalise(name: String): String = name.trim().lowercase()

    /** What this team calls [competencyKey], or null when no such competency exists. */
    @Transactional(readOnly = true)
    fun labelFor(competencyKey: String): String? = competencyRepository.findByKey(competencyKey)?.label

    /**
     * Records where the assessment placed [userId] on one competency.
     *
     * Never throws for a placement it will not make: an unknown key, an unusable level or a
     * competency already proven by real work all come back as [PlacementOutcome] with `recorded =
     * false` and a sentence the mentor can relay. The hire confirmed a button; a stack trace is not
     * an answer to that.
     */
    @Transactional
    fun record(userId: UUID, competencyKey: String, level: ProficiencyLevel): PlacementOutcome {
        val competency = competencyRepository.findByKey(competencyKey)
            ?: return PlacementOutcome(
                recorded = false,
                message = "I couldn't find that skill in this team's list, so I haven't recorded anything.",
            )

        val existing = userCompetencyStateRepository.findByUserIdAndCompetencyKey(userId, competencyKey)
        if (existing?.source == CompetencySource.VERIFIED) {
            return PlacementOutcome(
                recorded = false,
                message = "Your own accepted work already proves “${competency.label}” — I'd only be " +
                    "writing over something stronger, so I've left it as it is.",
            )
        }

        if (existing != null) {
            // Monotonic, exactly as accepted work is: a second conversation may raise a placement,
            // never lower one.
            existing.level = maxOf(existing.level, level.rank)
            existing.source = CompetencySource.ASSESSED
            existing.updatedAt = clock.instant()
        } else {
            userCompetencyStateRepository.save(
                UserCompetencyState(
                    userId = userId,
                    competencyKey = competencyKey,
                    level = level.rank,
                    source = CompetencySource.ASSESSED,
                ),
            )
        }

        return PlacementOutcome(
            recorded = true,
            message = "Noted — “${competency.label}” at ${level.word}. It's a starting point, not a " +
                "verdict: your work here is what will move it.",
        )
    }

    /**
     * The competency keys the claimable pool actually exercises.
     *
     * Read from the same `LIVE` pool [StarterWorkTaskProposalService] ranks tasks out of, so the
     * competencies an assessment prioritises and the tasks the buddy suggests can never be about
     * different work.
     */
    private fun keysTheLivePoolNeeds(): Set<String> =
        starterWorkTaskProposalRepository
            .findAllByStatus(ProposalStatus.LIVE)
            .flatMap { it.competencyKeys }
            .toSet()

    /** One competency an assessment could place the hire on. */
    data class PlacementTopic(
        val key: String,
        val label: String,
        val description: String?,
        val kind: String,
        /** Whether a task the hire could claim right now exercises it. */
        val neededByAvailableWork: Boolean,
    )

    /** What came of a confirmed placement, and the line to tell the hire. */
    data class PlacementOutcome(
        val recorded: Boolean,
        val message: String,
    )

    companion object {
        /** The most competencies one assessment is ever offered. */
        const val MAX_TOPICS = 12
    }
}
