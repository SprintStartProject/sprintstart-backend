package com.sprintstart.sprintstartbackend.insights.listener

import com.sprintstart.sprintstartbackend.insights.repository.ComponentOwnerRepository
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class ComponentOwnerUserDeletedListenerTest {
    @Test
    fun `deleting a user removes them as owner of every component`() {
        val owners: ComponentOwnerRepository = mockk(relaxed = true)
        val userId = UUID.randomUUID()

        ComponentOwnerUserDeletedListener(owners).onUserDeleted(UserDeletedEvent(userId))

        verify { owners.deleteAllByUserId(userId) }
    }
}
