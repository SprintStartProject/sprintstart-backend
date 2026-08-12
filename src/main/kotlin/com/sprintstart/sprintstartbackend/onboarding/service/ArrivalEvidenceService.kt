package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.connectors.github.GithubClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ArrivalDerivation
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStepState
import com.sprintstart.sprintstartbackend.onboarding.repository.ArrivalStepStateRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.enums.GithubLoginVerification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Checks the arrival steps the system can observe, and records what it saw.
 *
 * ⚠️ **Reading and checking are split.** [ArrivalStepService.forHire] is a pure database read and
 * never calls anything, because the arrival card is on every hire's board and a board that waits on
 * GitHub to open is a board nobody opens. This is what actually looks, and the client calls it once
 * after the board has rendered.
 *
 * ⚠️ **Observation settles a step; failing to observe never unsettles one.** Nothing here writes a
 * negative, deletes a state row, or reverses a hire's own confirmation. A GitHub outage, a rate
 * limit and a project with no contributions yet are indistinguishable at this level, and all three
 * mean the same thing: leave it alone.
 */
@Service
class ArrivalEvidenceService(
    private val arrivalStepService: ArrivalStepService,
    private val arrivalStepStateRepository: ArrivalStepStateRepository,
    private val contributionService: ContributionService,
    private val projectMembershipApi: ProjectMembershipApi,
    private val githubClient: GithubClient,
    private val userApi: UserApi,
    transactionManager: PlatformTransactionManager,
) {
    private val txTemplate = TransactionTemplate(transactionManager)
    private val readTxTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    /**
     * Re-checks everything observable for this caller and returns their steps as they now stand.
     *
     * @param authId The caller's auth subject.
     * @return Their arrival steps, including anything this call just settled.
     * @throws ResponseStatusException 404 when the subject resolves to no user.
     */
    suspend fun refreshForCaller(authId: String): List<ResolvedArrivalStep> {
        val userId =
            readTxTemplate.execute { userApi.getUserIdByAuthId(authId).orElse(null) }
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No user found with authId: $authId",
                )

        return refresh(userId)
    }

    /**
     * Re-checks everything observable for [userId].
     *
     * The transaction is deliberately not held across the GitHub call: the model this codebase
     * already follows for anything talking to a network is read-tx, then the call, then write-tx.
     */
    suspend fun refresh(userId: UUID): List<ResolvedArrivalStep> {
        val pending =
            readTxTemplate
                .execute {
                    arrivalStepService
                        .forHire(userId)
                        .filterNot { it.settled }
                        .mapNotNull { ArrivalDerivation.forStepKey(it.step.key) }
                }.orEmpty()

        if (pending.isEmpty()) {
            return readTxTemplate.execute { arrivalStepService.forHire(userId) }.orEmpty()
        }

        val observed = pending.filter { observes(userId, it) }

        return withContext(Dispatchers.IO) {
            txTemplate.execute {
                observed.forEach { settle(userId, it) }
                arrivalStepService.forHire(userId)
            }
        }.orEmpty()
    }

    /** Whether this derivation can currently see that the step is done. */
    private suspend fun observes(userId: UUID, derivation: ArrivalDerivation): Boolean =
        when (derivation) {
            ArrivalDerivation.GITHUB_ACCOUNT -> verifyGithubAccount(userId)
            ArrivalDerivation.ENVIRONMENT_READY -> hasProducedWork(userId)
        }

    /**
     * Asks GitHub whether the declared login exists, and stores what it said.
     *
     * The verdict is written whichever way it comes out, because a *found* answer settles the step
     * and a *not found* answer is what lets the app say "we could not find that account" instead of
     * silently failing to credit their work later. Only a refusal to answer writes nothing.
     */
    private suspend fun verifyGithubAccount(userId: UUID): Boolean {
        val login =
            readTxTemplate.execute { userApi.getGithubLoginByUserId(userId) }
                ?: return false

        // Null means GitHub would not say -- a rate limit, an outage. Not an answer, so not recorded:
        // telling somebody their perfectly good username does not exist is worse than saying nothing.
        val exists = githubClient.userExists(login) ?: return false

        withContext(Dispatchers.IO) {
            txTemplate.execute {
                userApi.recordGithubLoginVerification(
                    userId,
                    if (exists) GithubLoginVerification.VERIFIED else GithubLoginVerification.NOT_FOUND,
                )
            }
        }

        return exists
    }

    /**
     * Whether this hire has produced any work anywhere, which means their environment evidently ran.
     *
     * Across every project they belong to rather than one, because arrival is a fact about a person
     * — a machine that builds does not stop building when they open a different project.
     */
    private fun hasProducedWork(userId: UUID): Boolean =
        readTxTemplate.execute {
            arrivalStepService.projectsFor(userId).any { projectId ->
                val member = projectMembershipApi
                    .getProjectMembers(projectId)
                    .firstOrNull { it.userId == userId }

                member != null && contributionService.forHire(member, projectId).isNotEmpty()
            }
        } ?: false

    /**
     * Records that the system saw this step done. Idempotent, and never overwrites.
     *
     * Re-read inside the write transaction rather than trusting what the read transaction saw: the
     * hire may have confirmed the step themselves while the GitHub call was in flight, and their own
     * word arriving first is not something to overwrite with a later observation of the same fact.
     */
    private fun settle(userId: UUID, derivation: ArrivalDerivation) {
        val alreadySettled =
            arrivalStepStateRepository
                .findAllByUserId(userId)
                .any { it.stepKey == derivation.stepKey }

        if (alreadySettled) return

        arrivalStepStateRepository.save(
            ArrivalStepState(
                userId = userId,
                stepKey = derivation.stepKey,
                rigor = Rigor.OBSERVED,
            ),
        )
    }
}
