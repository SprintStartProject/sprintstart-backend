package com.sprintstart.sprintstartbackend.user.external.events

import org.junit.jupiter.api.Test
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertTrue

class OnUserDeletedTest {
    private class Listener {
        var received: UserDeletedEvent? = null

        @OnUserDeleted
        fun onUserDeleted(event: UserDeletedEvent) {
            received = event
        }
    }

    /** Spring finds both through the meta-annotation, so a marked method is a transactional listener. */
    @Test
    fun `a marked method is an event listener running in a transaction`() {
        val method = Listener::class.java.getDeclaredMethod("onUserDeleted", UserDeletedEvent::class.java)

        assertTrue(AnnotatedElementUtils.hasAnnotation(method, EventListener::class.java))
        assertTrue(AnnotatedElementUtils.hasAnnotation(method, Transactional::class.java))
    }
}
