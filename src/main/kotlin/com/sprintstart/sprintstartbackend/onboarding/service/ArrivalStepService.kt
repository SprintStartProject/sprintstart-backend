package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ArrivalDerivation
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContentProvenance
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStepState
import com.sprintstart.sprintstartbackend.onboarding.repository.ArrivalStepRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.ArrivalStepStateRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * One arrival step as it applies to one hire: the shared definition, plus whether they have settled
 * it.
 *
 * [settledAt] and [rigor] are null together and mean "not settled yet", which is a normal state on
 * day one rather than missing data.
 */
data class ResolvedArrivalStep(
    val step: ArrivalStep,
    val settledAt: Instant?,
    val rigor: Rigor?,
    /**
     * The name of the project this step is scoped to; null for a company-wide step.
     *
     * ⚠️ The client groups under this name, so it must be a name rather than an id — a hire's
     * list is a union across their projects, and the same title can appear once per project.
     */
    val projectName: String? = null,
) {
    val settled: Boolean get() = settledAt != null
}

/**
 * Owns arrival steps: what they are, who they apply to, and what a given hire has settled.
 *
 * ⚠️ **Nothing here blocks anything.** There is deliberately no method answering "may this hire
 * proceed", because no caller should be able to ask. An outstanding step changes what a hire is
 * *shown*, never what they are *allowed to do*. A gate would be a design change, not a missing
 * method.
 *
 * ⚠️ **Uniqueness is enforced here as well as in the database.** A step's key must be unique within
 * its scope, and company-wide steps carry `project_id = NULL`. **Postgres does not treat two NULLs
 * as conflicting**, so the rule needs two partial unique indexes — and Hibernate cannot express a
 * partial index, so the schema the test suite builds from these entities has neither. Without the
 * explicit checks below, the rule would hold in the database and quietly not hold in every test.
 *
 * Both audiences — the hire's read plus confirm, and the authoring behind it — share those scoping
 * and uniqueness rules, hence the function-count suppression.
 */
@Suppress("TooManyFunctions")
@Service
class ArrivalStepService(
    private val arrivalStepRepository: ArrivalStepRepository,
    private val arrivalStepStateRepository: ArrivalStepStateRepository,
    private val userApi: UserApi,
) {
    /**
     * [forHire] for a caller identified by their auth subject.
     *
     * @throws ResponseStatusException 404 when the subject resolves to no user.
     */
    @Transactional(readOnly = true)
    fun forCaller(authId: String): List<ResolvedArrivalStep> = forHire(resolveUserId(authId))

    /**
     * [confirm] for a caller identified by their auth subject.
     *
     * @throws ResponseStatusException 404 when the subject resolves to no user, or no such step
     * applies to them.
     */
    @Transactional
    fun confirmForCaller(authId: String, key: String): ResolvedArrivalStep =
        confirm(resolveUserId(authId), key)

    /**
     * Every arrival step that applies to [userId], each carrying whether they have settled it.
     *
     * The list is company-wide steps plus the steps of every project the hire belongs to,
     * deduplicated by [ArrivalStep.key] with a project-scoped definition winning — so a project can
     * sharpen a company step's wording without forking the key its state is stored against.
     *
     * ⚠️ Scoped to *all* the hire's projects rather than one: arrival is a fact about a person,
     * not about a project.
     *
     * @return Their steps, company-scoped first, each ordered by position within its own scope.
     * Empty when nobody has authored any steps, which is a real answer and not an error.
     */
    @Transactional(readOnly = true)
    fun forHire(userId: UUID): List<ResolvedArrivalStep> {
        val projectNames = projectNamesFor(userId)

        val companySteps = arrivalStepRepository.findAllByProjectIdIsNullOrderByPositionAsc()
        val projectSteps =
            if (projectNames.isEmpty()) {
                emptyList()
            } else {
                arrivalStepRepository.findAllByProjectIdInOrderByPositionAsc(projectNames.keys.toList())
            }

        // A project-scoped definition wins the key.
        val projectKeys = projectSteps.map { it.key }.toSet()

        // ⚠️ Ordered by scope, not by position across scopes: positions are assigned *within* a
        // scope, so sorting the union by them ranks numbers that were never comparable.
        // Within a scope position still decides; across scopes, company first, then projects by
        // name, so the order is stable rather than dependent on however the ids came back.
        val ordered =
            companySteps.filterNot { it.key in projectKeys } +
                projectSteps.sortedWith(
                    compareBy<ArrivalStep> { projectNames[it.projectId].orEmpty() }
                        .thenBy { it.position },
                )

        val statesByKey = arrivalStepStateRepository.findAllByUserId(userId).associateBy { it.stepKey }

        return ordered.map { step ->
            val state = statesByKey[step.key]
            ResolvedArrivalStep(
                step = step,
                settledAt = state?.settledAt,
                rigor = state?.rigor,
                projectName = step.projectId?.let { projectNames[it] },
            )
        }
    }

    /**
     * Records that [userId] says they have done the step [key].
     *
     * ⚠️ Idempotent: settling an already-settled step returns what is already there and leaves
     * [ArrivalStepState.settledAt] alone — the day something happened does not move.
     *
     * @throws ResponseStatusException 404 when no step with that key applies to this hire; 400 when
     * the step is settled by observation rather than by the hire.
     */
    @Transactional
    fun confirm(userId: UUID, key: String): ResolvedArrivalStep {
        val resolved =
            forHire(userId).firstOrNull { it.step.key == key }
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No arrival step '$key' applies to this user",
                )

        // ⚠️ `selfConfirmable` is not a synonym for `settledBy`. Some derived steps are still the
        // hire's to claim -- "my machine builds" is observable but never refutable. Others are not:
        // the GitHub check is definitive when it answers, and letting somebody tick it would let
        // them declare away the fact their credit depends on.
        if (!resolved.step.selfConfirmable) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Arrival step '$key' is one the system checks, not one you confirm",
            )
        }

        if (resolved.settled) {
            return resolved
        }

        val state =
            arrivalStepStateRepository.save(
                ArrivalStepState(
                    userId = userId,
                    stepKey = key,
                    projectId = resolved.step.projectId,
                    rigor = Rigor.DECLARED,
                ),
            )

        return resolved.copy(settledAt = state.settledAt, rigor = state.rigor)
    }

    /**
     * The steps the system knows how to check, and whether each is already on the list.
     *
     * ⚠️ Nothing is seeded from this — an admin adds the ones their organisation wants, which is
     * what keeps a local-build step off the board of somebody who never builds anything.
     */
    @Transactional(readOnly = true)
    fun derivable(): List<Pair<ArrivalDerivation, Boolean>> {
        val present = arrivalStepRepository
            .findAllByProjectIdIsNullOrderByPositionAsc()
            .map { it.key }
            .toSet()

        return ArrivalDerivation.entries.map { it to (it.stepKey in present) }
    }

    /** Every authored step in a scope, for the authoring surface. */
    @Transactional(readOnly = true)
    fun listForAuthoring(projectId: UUID?): List<ArrivalStep> {
        return if (projectId == null) {
            arrivalStepRepository.findAllByProjectIdIsNullOrderByPositionAsc()
        } else {
            arrivalStepRepository.findAllByProjectIdInOrderByPositionAsc(listOf(projectId))
        }
    }

    /**
     * Creates a step.
     *
     * @throws ResponseStatusException 400 when the key is blank or malformed; 409 when a step with
     * that key already exists in the same scope.
     */
    @Transactional
    fun create(
        key: String,
        projectId: UUID?,
        title: String,
        description: String?,
        href: String?,
        position: Int,
        settledBy: Rigor,
    ): ArrivalStep {
        val normalizedKey = normalizeKey(key)
        requireKeyFree(normalizedKey, projectId)

        if (title.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "An arrival step needs a title")
        }

        // ⚠️ A key the system knows how to check is always created as a derived step, whatever
        // the caller asked for -- a row that looks derived and is not fails silently.
        val derivation = ArrivalDerivation.forStepKey(normalizedKey)

        return arrivalStepRepository.save(
            ArrivalStep(
                key = normalizedKey,
                projectId = projectId,
                title = title.trim(),
                description = description?.trim()?.ifBlank { null },
                href = href?.trim()?.ifBlank { null },
                position = position,
                settledBy = if (derivation != null) Rigor.OBSERVED else settledBy,
                selfConfirmable = derivation?.selfConfirmable ?: true,
                provenance = ContentProvenance.PM,
            ),
        )
    }

    /**
     * Updates a step's wording, link, ordering or settlement mechanism.
     *
     * ⚠️ The key is **not** updatable. State points at it, so changing it would orphan every
     * hire's record of having done the step while leaving the row looking healthy.
     *
     * @throws ResponseStatusException 404 when no such step exists in that scope; 400 on a blank
     * title.
     */
    @Transactional
    fun update(
        key: String,
        projectId: UUID?,
        title: String?,
        description: String?,
        href: String?,
        position: Int?,
        settledBy: Rigor?,
    ): ArrivalStep {
        val step = findOrThrow(key, projectId)

        title?.let {
            if (it.isBlank()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "An arrival step needs a title")
            }
            step.title = it.trim()
        }
        description?.let { step.description = it.trim().ifBlank { null } }
        href?.let { step.href = it.trim().ifBlank { null } }
        position?.let { step.position = it }
        settledBy?.let { step.settledBy = it }
        step.provenance = ContentProvenance.PM

        return arrivalStepRepository.save(step)
    }

    /**
     * Applies a whole ordering at once.
     *
     * ⚠️ Takes the complete list rather than a from/to pair, so two people reordering
     * concurrently cannot interleave into an order neither of them chose.
     *
     * @throws ResponseStatusException 404 when a key in [orderedKeys] is not a step in that scope.
     */
    @Transactional
    fun reorder(projectId: UUID?, orderedKeys: List<String>): List<ArrivalStep> {
        val steps = listForAuthoring(projectId).associateBy { it.key }

        orderedKeys.forEachIndexed { index, key ->
            val step =
                steps[key]
                    ?: throw ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No arrival step '$key' in this scope",
                    )
            step.position = index
        }

        return arrivalStepRepository.saveAll(steps.values.sortedBy { it.position })
    }

    /**
     * Deletes a step definition. **Hires' state survives**, by design.
     *
     * ⚠️ [ArrivalStepState] is keyed by the step's key rather than by a foreign key, so removing
     * a definition removes it from everybody's list without destroying the record that somebody
     * did it — and re-adding the same key restores those records.
     *
     * @throws ResponseStatusException 404 when no such step exists in that scope.
     */
    @Transactional
    fun delete(key: String, projectId: UUID?) {
        arrivalStepRepository.delete(findOrThrow(key, projectId))
    }

    private fun findOrThrow(key: String, projectId: UUID?): ArrivalStep {
        val step =
            if (projectId == null) {
                arrivalStepRepository.findByKeyAndProjectIdIsNull(key)
            } else {
                arrivalStepRepository.findByKeyAndProjectId(key, projectId)
            }

        return step
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No arrival step '$key' in this scope")
    }

    private fun requireKeyFree(key: String, projectId: UUID?) {
        val taken =
            if (projectId == null) {
                arrivalStepRepository.existsByKeyAndProjectIdIsNull(key)
            } else {
                arrivalStepRepository.existsByKeyAndProjectId(key, projectId)
            }

        if (taken) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "An arrival step '$key' already exists in this scope",
            )
        }
    }

    private fun normalizeKey(key: String): String {
        val normalized = key.trim().lowercase()

        if (!KEY.matches(normalized)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "'$key' is not a valid arrival step key (lower-case letters, digits, '-' and '_')",
            )
        }

        return normalized
    }

    private fun resolveUserId(authId: String): UUID =
        userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

    /** Every project this hire belongs to. Exposed for [ArrivalEvidenceService]'s derivations. */
    fun projectsFor(userId: UUID): List<UUID> = projectIdsFor(userId)

    private fun projectIdsFor(userId: UUID): List<UUID> = projectNamesFor(userId).keys.toList()

    /** This hire's projects by id, with the names their steps are grouped under. */
    private fun projectNamesFor(userId: UUID): Map<UUID, String> =
        userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()
            .associate { it.projectId to it.name }

    private companion object {
        val KEY = Regex("^[a-z\\d][a-z\\d_-]{0,63}$")
    }
}
