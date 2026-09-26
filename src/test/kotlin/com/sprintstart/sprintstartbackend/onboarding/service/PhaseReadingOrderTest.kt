package com.sprintstart.sprintstartbackend.onboarding.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The phase's reading order has to be the frontend's, item for item, or the page's "up next" and the
 * buddy's next thing drift apart.
 */
class PhaseReadingOrderTest {
    private val ids = listOf("s1", "s2", "s3", "s4", "q1", "q2").associateWith { UUID.randomUUID() }

    private fun node(name: String, vararg waitsOn: String) =
        ids.getValue(name) to waitsOn.map { ids.getValue(it) }.toSet()

    private fun names(order: List<UUID>) = order.map { id -> ids.entries.first { it.value == id }.key }

    @Test
    fun `a graph with no edges reads in the order it came in`() {
        assertThat(names(PhaseReadingOrder.of(listOf(node("s1"), node("s2"), node("q1")))))
            .containsExactly("s1", "s2", "q1")
    }

    @Test
    fun `what an item waits on reads before it`() {
        assertThat(names(PhaseReadingOrder.of(listOf(node("s1", "q1"), node("q1")))))
            .containsExactly("q1", "s1")
    }

    /**
     * Produced by the frontend's `orderByGraph` for the same input -- including `s2` pulled in front of
     * `s1` by the barycentre pass, which is the part a simpler rule would get wrong.
     */
    @Test
    fun `a mixed graph reads exactly as the frontend orders it`() {
        val order = PhaseReadingOrder.of(
            listOf(
                node("s1"),
                node("s2"),
                node("s3", "s2"),
                node("s4", "s1", "q1"),
                node("q1"),
                node("q2", "s3"),
            ),
        )

        assertThat(names(order)).containsExactly("s2", "s1", "q1", "s3", "s4", "q2")
    }

    @Test
    fun `a cycle is cut rather than recursed into forever`() {
        assertThat(names(PhaseReadingOrder.of(listOf(node("s1", "s2"), node("s2", "s1")))))
            .containsExactlyInAnyOrder("s1", "s2")
    }
}
