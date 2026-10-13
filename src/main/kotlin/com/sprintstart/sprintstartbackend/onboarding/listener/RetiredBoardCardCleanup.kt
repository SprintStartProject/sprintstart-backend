package com.sprintstart.sprintstartbackend.onboarding.listener

import com.sprintstart.sprintstartbackend.onboarding.repository.BoardCardRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Deletes board cards whose kind was retired, once, when the application starts.
 *
 * A card row of a kind the enum no longer has cannot be read through the entity, so a single
 * leftover would fail every board read for that hire. `ddl-auto: update` never removes rows, and
 * the SQL migrations are not run automatically, so the delete happens here instead. Idempotent: once
 * the rows are gone it deletes nothing.
 */
@Component
class RetiredBoardCardCleanup(
    private val boardCardRepository: BoardCardRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Not transactional itself: the delete runs in the repository's own transaction, so a failure
     * rolls back there and is caught here, rather than marking an outer transaction rollback-only
     * and failing startup on commit. Housekeeping must never be what stops the application starting.
     */
    @EventListener(ApplicationReadyEvent::class)
    @Suppress("TooGenericExceptionCaught")
    fun removeRetiredCards() {
        try {
            val removed = boardCardRepository.deleteRetiredKinds()
            if (removed > 0) logger.info("Removed {} board cards of retired kinds", removed)
        } catch (e: RuntimeException) {
            logger.warn("Could not remove board cards of retired kinds; see V19__retire_legacy_onboarding.sql", e)
        }
    }
}
