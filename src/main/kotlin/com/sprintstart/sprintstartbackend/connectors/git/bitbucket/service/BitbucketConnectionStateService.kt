package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Tracks the visible sync state of one connected repository around its collector jobs.
 *
 * `connectionState` is what the rest of the application reads to answer "is this repository syncing,
 * current, or broken". Without a writer it sat on its initial `UP_TO_DATE` forever, so a clone that
 * failed every night looked exactly as healthy as one that had never failed.
 *
 * The state is written by [markUpdating] up front and finalized by [awaitCollectorsAndFinalize],
 * which awaits the results of all three collector jobs, so the terminal state lands after the work
 * it describes rather than alongside it. The collectors themselves are untouched: they keep their
 * own cursor logic and single-purpose saves, and pay no extra write for state tracking.
 *
 * @constructor Creates the service from its repository.
 * @param connectionRepository Owns the connection rows the state is written to.
 */
@Service
internal class BitbucketConnectionStateService(
    private val connectionRepository: BitbucketConnectionRepository,
) {
    /**
     * Persists `UPDATING` for the connection before its collectors start.
     *
     * Uses a targeted state update rather than saving a loaded entity, so it neither overwrites a
     * cursor another job may have advanced in the meantime, nor pays loading the whole row.
     *
     * @param repositoryId The connection entering an update.
     */
    @Transactional
    @Tracked("Marking Bitbucket repository as updating")
    fun markUpdating(repositoryId: UUID) {
        connectionRepository.updateConnectionState(repositoryId, ConnectionState.UPDATING)
    }

    /**
     * Awaits the results of the collector jobs of one update, then persists the state they produced.
     *
     * All three collectors are awaited as deferred results: a collector's own exception surfaces
     * here rather than vanishing into a launched job's log, and the first one to fail ends the wait.
     * `FAILED` is written when any of them threw, `UP_TO_DATE` only when all succeeded. A failure in
     * one collector must not hide the cursors another advanced, so nothing is rolled back — the
     * cursors are honest per collector, and the state records that the *update* failed.
     *
     * @param repositoryId The connection being updated.
     * @param collectors One deferred per collector, as built by the caller with `async`.
     * @throws IllegalStateException when a collector threw, after the failure state was persisted.
     */
    @Tracked("Finalizing the state of a Bitbucket repository update")
    suspend fun awaitCollectorsAndFinalize(
        repositoryId: UUID,
        collectors: List<Deferred<Unit>>,
    ) {
        try {
            collectors.awaitAll()
        } catch (first: Exception) {
            persistState(repositoryId, ConnectionState.FAILED)
            throw IllegalStateException("Updating Bitbucket repository $repositoryId failed", first)
        }
        persistState(repositoryId, ConnectionState.UP_TO_DATE)
    }

    /**
     * Writes the given state unless the connection is gone.
     *
     * A connection deleted mid-update is skipped rather than resurrected: there is nothing left to
     * mark, and recreating the row would lose the deletion. Only existence is read — never the row
     * itself — so the write cannot carry a stale snapshot over a cursor another job just advanced.
     *
     * @param repositoryId The connection to update.
     * @param state The state to persist.
     */
    private suspend fun persistState(repositoryId: UUID, state: ConnectionState) {
        val exists = withContext(Dispatchers.IO) {
            connectionRepository.existsById(repositoryId)
        }
        if (!exists) return

        withContext(Dispatchers.IO) {
            connectionRepository.updateConnectionState(repositoryId, state)
        }
    }
}
