package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.ArtifactAiIngestRequest
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.RunArtifactsAiSyncRequest
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactAiSyncState
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ingestion.ArtifactAiMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Drains the artifact AI-sync outbox: sends artifacts to the AI index as they become available,
 * instead of waiting for a whole ingestion run to finish.
 *
 * A crawl of a large repository spends most of its time fetching from GitHub, while every artifact
 * is individually embeddable the moment it is stored. Artifacts are therefore marked
 * [ArtifactAiSyncState.PENDING] on create *and* on change, and this service repeatedly takes a
 * small batch and syncs it — so content becomes searchable during the run rather than only after
 * it completes.
 *
 * Failure handling is per artifact, which is the other half of the point. The AI service
 * acknowledges each artifact individually (and never fails a batch as a whole), so only artifacts
 * that actually failed are retried; each retry costs one of [maxAttempts] with an exponential
 * backoff, after which the artifact is parked as [ArtifactAiSyncState.FAILED] rather than looping
 * forever.
 */
@Service
class ArtifactAiSyncService(
    private val artifactRepository: ArtifactRepository,
    private val artifactAiMapper: ArtifactAiMapper,
    private val artifactIngestionClient: ArtifactIngestionClient,
    private val ingestionRunAiSyncStatusService: IngestionRunAiSyncStatusService,
    transactionManager: PlatformTransactionManager,
    @Value("\${sprintstart.ingestion.ai-sync.batch-size:25}")
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    @Value("\${sprintstart.ingestion.ai-sync.max-attempts:5}")
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    @Value("\${sprintstart.ingestion.ai-sync.retry-base-seconds:30}")
    private val retryBaseSeconds: Long = DEFAULT_RETRY_BASE_SECONDS,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Reads plus the artifactAiMapper.toIngestRequest mapping must share one session: Artifact.labels
    // is lazy, so mapping it after the session closes throws LazyInitializationException.
    private val readTxTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val writeTxTemplate = TransactionTemplate(transactionManager)

    /**
     * Syncs at most one batch of pending artifacts and records the outcome of each.
     *
     * Split into read transaction -> HTTP call -> write transaction so the AI request never runs
     * inside an open transaction (the same shape the rest of this codebase uses for AI-adjacent
     * work). Every affected run's `aiSyncStatus` is recomputed afterwards, so a run reports
     * `SUCCEEDED` only once every artifact attributed to it is indexed.
     *
     * @return The number of artifacts sent in this batch; `0` when the outbox is empty, which the
     * scheduler treats as "nothing to do".
     */
    suspend fun drainOnce(): Int {
        val batch = withContext(Dispatchers.IO) {
            readTxTemplate.execute { loadBatch() }
        } ?: EMPTY_BATCH

        if (batch.isEmpty()) {
            return 0
        }

        logger.info("Syncing {} pending artifact(s) to the AI index", batch.size)

        val acknowledged: Map<String, String> = try {
            artifactIngestionClient
                .ingest(RunArtifactsAiSyncRequest(artifactsToIngest = batch.requests, artifactsToDeindex = emptyList()))
                .artifacts
                .associate { it.artifactId to it.status }
        } catch (e: Exception) {
            // The whole call failed (AI service down, network, non-2xx): nothing in this batch was
            // acknowledged, so every artifact in it stays owed and burns one attempt.
            logger.error("AI sync request failed for {} artifact(s)", batch.size, e)
            emptyMap()
        }

        val failureReason = if (acknowledged.isEmpty()) "AI sync request failed" else null
        withContext(Dispatchers.IO) {
            writeTxTemplate.execute { applyResults(batch.ids, acknowledged, failureReason) }
        }

        batch.runIds.forEach { ingestionRunAiSyncStatusService.recompute(it) }

        return batch.size
    }

    private fun loadBatch(): Batch {
        val artifacts = artifactRepository.findPendingAiSync(Instant.now(), PageRequest.of(0, batchSize))

        return Batch(
            ids = artifacts.map { it.id },
            requests = artifacts.map { artifactAiMapper.toIngestRequest(it) },
            runIds = artifacts.mapNotNull { it.aiSyncRunId }.toSet(),
        )
    }

    /**
     * Applies the AI service's per-artifact acknowledgements.
     *
     * An artifact is only marked synced when the AI service explicitly reported `completed` for it.
     * Anything else -- an explicit `failed`, or an id the response never mentioned -- counts as a
     * failed attempt, because "not acknowledged" and "acknowledged as failed" are equally not
     * indexed.
     */
    private fun applyResults(ids: List<UUID>, acknowledged: Map<String, String>, failureReason: String?) {
        val now = Instant.now()

        artifactRepository.findAllById(ids).forEach { artifact ->
            val status = acknowledged[artifact.id.toString()]
            if (status == STATUS_COMPLETED) {
                markSynced(artifact, now)
            } else {
                markAttemptFailed(artifact, now, failureReason ?: reasonFor(status))
            }
        }
    }

    private fun markSynced(artifact: Artifact, now: Instant) {
        artifact.aiSyncState = ArtifactAiSyncState.SYNCED
        artifact.aiSyncedAt = now
        artifact.aiSyncError = null
        artifact.aiSyncNextAttemptAt = null
    }

    private fun markAttemptFailed(artifact: Artifact, now: Instant, reason: String) {
        artifact.aiSyncAttempts++
        artifact.aiSyncError = reason

        if (artifact.aiSyncAttempts >= maxAttempts) {
            artifact.aiSyncState = ArtifactAiSyncState.FAILED
            artifact.aiSyncNextAttemptAt = null
            logger.warn(
                "Giving up on AI sync for artifact {} after {} attempts: {}",
                artifact.id,
                artifact.aiSyncAttempts,
                reason,
            )
            return
        }

        // Exponential backoff so a persistent AI-service outage doesn't turn into a hot loop.
        artifact.aiSyncNextAttemptAt = now.plus(
            Duration.ofSeconds(retryBaseSeconds shl (artifact.aiSyncAttempts - 1)),
        )
    }

    private fun reasonFor(status: String?): String =
        if (status == null) "AI service did not acknowledge this artifact" else "AI service reported '$status'"

    private data class Batch(
        val ids: List<UUID>,
        val requests: List<ArtifactAiIngestRequest>,
        val runIds: Set<UUID>,
    ) {
        val size: Int get() = ids.size

        fun isEmpty(): Boolean = ids.isEmpty()
    }

    private companion object {
        val EMPTY_BATCH = Batch(emptyList(), emptyList(), emptySet())
        const val STATUS_COMPLETED = "completed"
        const val DEFAULT_BATCH_SIZE = 25
        const val DEFAULT_MAX_ATTEMPTS = 5
        const val DEFAULT_RETRY_BASE_SECONDS = 30L
    }
}
