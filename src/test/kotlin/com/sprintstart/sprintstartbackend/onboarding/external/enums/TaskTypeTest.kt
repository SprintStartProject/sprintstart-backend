package com.sprintstart.sprintstartbackend.onboarding.external.enums

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TaskTypeTest {
    @Test
    fun `reads the type off the project's own label vocabulary`() {
        assertEquals(TaskType.BUG, TaskType.fromLabels(listOf("bug")))
        assertEquals(TaskType.DOCS, TaskType.fromLabels(listOf("documentation")))
        assertEquals(TaskType.FEATURE, TaskType.fromLabels(listOf("enhancement")))
        assertEquals(TaskType.TEST, TaskType.fromLabels(listOf("needs tests")))
        assertEquals(TaskType.CHORE, TaskType.fromLabels(listOf("refactor")))
    }

    @Test
    fun `tolerates the prefixes teams put on labels`() {
        assertEquals(TaskType.BUG, TaskType.fromLabels(listOf("type: bug")))
        assertEquals(TaskType.DOCS, TaskType.fromLabels(listOf("kind/docs")))
    }

    @Test
    fun `prefers the narrower label when an issue carries several`() {
        // What somebody will actually do here is write docs, even though it is filed as a bug.
        assertEquals(TaskType.DOCS, TaskType.fromLabels(listOf("bug", "documentation")))
    }

    @Test
    fun `an unlabelled issue is OTHER, which means unknown`() {
        assertEquals(TaskType.OTHER, TaskType.fromLabels(emptyList()))
        assertEquals(TaskType.OTHER, TaskType.fromLabels(listOf("priority: high")))
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals(TaskType.BUG, TaskType.fromLabels(listOf("Bug")))
    }
}
