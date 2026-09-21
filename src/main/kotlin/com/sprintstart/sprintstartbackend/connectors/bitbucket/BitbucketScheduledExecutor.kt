package com.sprintstart.sprintstartbackend.connectors.bitbucket

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Registers and regularly runs jobs within a given schedule for the Bitbucket connector module.
 *
 * The tick deliberately does nothing yet: periodic synchronization needs the connection store and
 * update service this module still grows, and wiring a schedule to GitHub's services here would
 * silently sync the wrong connector. Once the Bitbucket update service exists, the tick queries
 * the connections that are due for sync and launches their updates, mirroring the GitHub executor.
 */
@Component
class BitbucketScheduledExecutor {
    @Scheduled(fixedRate = 60_000)
    fun tick() {
        // TODO(#305): query Bitbucket connections due for sync and launch their updates.
    }
}
