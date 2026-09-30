package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.model.entity.DashboardLayout
import com.sprintstart.sprintstartbackend.user.model.entity.DashboardLayoutItemPayload
import com.sprintstart.sprintstartbackend.user.model.request.dashboard.SaveDashboardLayoutRequest
import com.sprintstart.sprintstartbackend.user.repository.DashboardLayoutRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

class DashboardLayoutServiceTest {
    private val dashboardLayoutRepository: DashboardLayoutRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk()

    private lateinit var service: DashboardLayoutService

    private val authId = "auth-alice"
    private val userId: UUID = UUID.randomUUID()
    private val otherAuthId = "auth-bob"
    private val otherUserId: UUID = UUID.randomUUID()

    private val items = listOf(
        DashboardLayoutItemPayload(id = "greeting", size = "wide"),
        DashboardLayoutItemPayload(id = "onboarding", size = "medium"),
        DashboardLayoutItemPayload(id = "skills", size = "small"),
    )

    @BeforeEach
    fun setUp() {
        service = DashboardLayoutService(dashboardLayoutRepository, userRepository)
        every { userRepository.findIdByAuthId(authId) } returns Optional.of(userId)
        every { userRepository.findIdByAuthId(otherAuthId) } returns Optional.of(otherUserId)
    }

    @Test
    fun `a user who never arranged anything gets the default rather than an error`() {
        every { dashboardLayoutRepository.findByUserId(userId) } returns null

        val read = service.read(authId, 2)

        assertTrue(read.items.isEmpty())
        assertNull(read.updatedAt)
        assertEquals(2, read.version)
    }

    @Test
    fun `an arrangement survives the round trip in order and with its sizes`() {
        val payload = slot<String>()
        every {
            dashboardLayoutRepository.upsert(any(), userId, 2, capture(payload), any())
        } returns Unit

        val written = service.write(authId, SaveDashboardLayoutRequest(version = 2, items = items))
        assertNotNull(written.updatedAt)

        every { dashboardLayoutRepository.findByUserId(userId) } returns stored(userId, 2, payload.captured)

        val read = service.read(authId, 2)

        assertEquals(items, read.items)
        assertEquals(2, read.version)
        assertNotNull(read.updatedAt)
    }

    @Test
    fun `an arrangement emptied on purpose reads back as empty but not as the default`() {
        every { dashboardLayoutRepository.findByUserId(userId) } returns stored(userId, 2, "[]")

        val read = service.read(authId, 2)

        assertTrue(read.items.isEmpty())
        assertNotNull(read.updatedAt)
    }

    @Test
    fun `a layout stored under another version falls back to the default`() {
        val payload = """[{"id":"greeting","size":"large"}]"""
        every { dashboardLayoutRepository.findByUserId(userId) } returns stored(userId, 1, payload)

        val read = service.read(authId, 2)

        // Discarded rather than half-migrated, the same trade the client makes.
        assertTrue(read.items.isEmpty())
        assertNull(read.updatedAt)
        assertEquals(2, read.version)
    }

    @Test
    fun `a layout that can no longer be parsed answers as the default instead of failing`() {
        every { dashboardLayoutRepository.findByUserId(userId) } returns stored(userId, 2, "not json")

        val read = service.read(authId, 2)

        assertTrue(read.items.isEmpty())
        assertNull(read.updatedAt)
    }

    @Test
    fun `a user only ever reads their own layout, never somebody else's`() {
        every { dashboardLayoutRepository.findByUserId(otherUserId) } returns
            stored(otherUserId, 2, """[{"id":"team-overview","size":"wide"}]""")
        every { dashboardLayoutRepository.findByUserId(userId) } returns null

        val read = service.read(authId, 2)

        assertTrue(read.items.isEmpty())
        verify(exactly = 1) { dashboardLayoutRepository.findByUserId(userId) }
        verify(exactly = 0) { dashboardLayoutRepository.findByUserId(otherUserId) }
    }

    @Test
    fun `a write lands on the caller's row and nobody else's`() {
        service.write(authId, SaveDashboardLayoutRequest(version = 2, items = items))

        verify(exactly = 1) { dashboardLayoutRepository.upsert(any(), userId, 2, any(), any()) }
        verify(exactly = 0) { dashboardLayoutRepository.upsert(any(), otherUserId, any(), any(), any()) }
    }

    @Test
    fun `a reset forgets only the caller's layout`() {
        service.clear(authId)

        verify(exactly = 1) { dashboardLayoutRepository.deleteByUserId(userId) }
        verify(exactly = 0) { dashboardLayoutRepository.deleteByUserId(otherUserId) }
    }

    @Test
    fun `an unknown caller is a 404`() {
        every { userRepository.findIdByAuthId("nobody") } returns Optional.empty()

        val error = assertThrows<ResponseStatusException> { service.read("nobody", 2) }

        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
    }

    private fun stored(owner: UUID, version: Int, payload: String) = DashboardLayout(
        userId = owner,
        version = version,
        payload = payload,
        updatedAt = Instant.parse("2026-09-30T10:00:00Z"),
    )
}
