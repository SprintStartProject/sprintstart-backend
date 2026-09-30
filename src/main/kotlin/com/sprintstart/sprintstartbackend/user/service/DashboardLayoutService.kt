package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.model.entity.DashboardLayout
import com.sprintstart.sprintstartbackend.user.model.entity.DashboardLayoutItemPayload
import com.sprintstart.sprintstartbackend.user.model.request.dashboard.SaveDashboardLayoutRequest
import com.sprintstart.sprintstartbackend.user.model.response.dashboard.DashboardLayoutResponse
import com.sprintstart.sprintstartbackend.user.repository.DashboardLayoutRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Reads, writes and forgets how a user has arranged their dashboard.
 *
 * Every method takes the caller's auth id and nothing else that names a user, so there is no way
 * to ask for somebody else's arrangement — a layout is a personal preference, and whether a PM
 * should ever see a hire's is a product question this deliberately does not answer.
 *
 * **Versioned like the client does it.** The client stamps a layout with its `LAYOUT_VERSION` and
 * throws a layout of another version away rather than half-migrating it. The server does the
 * same: a read names the version the client understands, and a stored layout of any other version
 * answers exactly as "nothing stored" does, so the client falls back to its default. The version is
 * the client's rather than a constant here because only the client knows what its widgets and
 * sizes mean — a server-side number would have to be bumped in lockstep with every frontend change
 * to the vocabulary.
 */
@Service
class DashboardLayoutService(
    private val dashboardLayoutRepository: DashboardLayoutRepository,
    private val userRepository: UserRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * The caller's arrangement, or an empty one with a null `updatedAt` when there is none to use.
     *
     * "None to use" covers no row, a row written under a different [version], and a row that can
     * no longer be parsed. All three mean the same thing to the client — show the default — and a
     * 404 for the most ordinary state of a new user would make the client treat real errors the
     * same way.
     */
    @Transactional(readOnly = true)
    fun read(authId: String, version: Int): DashboardLayoutResponse {
        val userId = resolveUserId(authId)
        val stored = dashboardLayoutRepository.findByUserId(userId)
            ?: return defaultLayout(version)

        if (stored.version != version) return defaultLayout(version)

        val items = decode(stored) ?: return defaultLayout(version)

        return DashboardLayoutResponse(stored.version, items, stored.updatedAt)
    }

    /**
     * Replaces the caller's arrangement.
     *
     * Last write wins: two tabs are the realistic conflict, and an arrangement is a statement about
     * the whole dashboard that cannot be merged with another one.
     */
    @Transactional
    fun write(authId: String, request: SaveDashboardLayoutRequest): DashboardLayoutResponse {
        val userId = resolveUserId(authId)
        val now = Instant.now()

        dashboardLayoutRepository.upsert(
            id = UUID.randomUUID(),
            userId = userId,
            version = request.version,
            payload = json.encodeToString(itemsSerializer, request.items),
            now = now,
        )

        return DashboardLayoutResponse(request.version, request.items, now)
    }

    /** Forgets the caller's arrangement, so the next read answers with the default. */
    @Transactional
    fun clear(authId: String) {
        dashboardLayoutRepository.deleteByUserId(resolveUserId(authId))
    }

    private fun decode(stored: DashboardLayout): List<DashboardLayoutItemPayload>? =
        try {
            json.decodeFromString(itemsSerializer, stored.payload)
        } catch (e: SerializationException) {
            logger.warn(
                "Dashboard layout {} of user {} could not be read and was answered as the default; " +
                    "the next write replaces it",
                stored.id,
                stored.userId,
                e,
            )
            null
        }

    private fun resolveUserId(authId: String): UUID =
        userRepository.findIdByAuthId(authId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User with authId: $authId not found")
        }

    private fun defaultLayout(version: Int): DashboardLayoutResponse =
        DashboardLayoutResponse(version, emptyList(), null)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val itemsSerializer = ListSerializer(DashboardLayoutItemPayload.serializer())
    }
}
