package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.TaskType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StarterWorkMatcherTest {
    private fun profile(
        competencies: Set<String> = emptySet(),
        repositories: Set<String> = emptySet(),
        labels: Set<String> = emptySet(),
        taskTypes: Set<TaskType> = emptySet(),
    ) = StarterWorkMatcher.HireProfile(
        competencyKeys = competencies,
        familiarRepositories = repositories,
        familiarLabels = labels,
        familiarTaskTypes = taskTypes,
    )

    private fun task(
        competencies: Set<String> = emptySet(),
        type: TaskType = TaskType.OTHER,
        labels: Set<String> = emptySet(),
        repository: String? = "acme/api",
        reviewed: Boolean = true,
    ) = StarterWorkMatcher.TaskFeatures(
        competencyKeys = competencies,
        taskType = type,
        labels = labels,
        repositoryFullName = repository,
        reviewed = reviewed,
    )

    @Test
    fun `competency overlap is one input, not the whole score`() {
        val hire = profile(
            competencies = setOf("kotlin"),
            repositories = setOf("acme/api"),
            taskTypes = setOf(TaskType.BUG),
        )

        val overlapOnly = StarterWorkMatcher.score(hire, task(competencies = setOf("kotlin")), null)
        val overlapPlusContext = StarterWorkMatcher.score(
            hire,
            task(competencies = setOf("kotlin"), type = TaskType.BUG, repository = "acme/api"),
            null,
        )

        // Same competency match; the second task wins on the signals the old ranking ignored.
        assertTrue(overlapPlusContext.score > overlapOnly.score)
    }

    @Test
    fun `every contributing signal produces a reason the hire can read`() {
        val hire = profile(
            competencies = setOf("kotlin"),
            repositories = setOf("acme/api"),
            labels = setOf("good first issue"),
            taskTypes = setOf(TaskType.BUG),
        )

        val scored = StarterWorkMatcher.score(
            hire,
            task(
                competencies = setOf("kotlin"),
                type = TaskType.BUG,
                labels = setOf("good first issue"),
                repository = "acme/api",
            ),
            null,
        )

        assertEquals(4, scored.reasons.size)
        // Strongest signal first, so a client showing only one line shows the best one.
        assertContains(scored.reasons.first(), "kotlin")
        assertTrue(scored.reasons.any { it.contains("bug fix") })
        assertTrue(scored.reasons.any { it.contains("acme/api") })
        assertTrue(scored.reasons.any { it.contains("good first issue") })
    }

    @Test
    fun `a hire with no history gets a nudge toward forgiving work, not a verdict`() {
        val newcomer = profile()

        val docs = StarterWorkMatcher.score(newcomer, task(type = TaskType.DOCS), null)
        val refactor = StarterWorkMatcher.score(newcomer, task(type = TaskType.CHORE), null)

        assertTrue(docs.score > refactor.score)
        // The nudge is smaller than any real signal, so one piece of evidence outranks it.
        val withEvidence = StarterWorkMatcher.score(
            profile(competencies = setOf("kotlin")),
            task(competencies = setOf("kotlin"), type = TaskType.CHORE),
            null,
        )
        assertTrue(withEvidence.score > docs.score)
    }

    @Test
    fun `a hire with strong repo history ranks familiar work first`() {
        val veteran = profile(repositories = setOf("acme/api"), taskTypes = setOf(TaskType.BUG))

        val familiar = StarterWorkMatcher.score(veteran, task(type = TaskType.BUG, repository = "acme/api"), null)
        val unfamiliar = StarterWorkMatcher.score(veteran, task(type = TaskType.DOCS, repository = "acme/web"), null)

        assertTrue(familiar.score > unfamiliar.score)
        assertContains(familiar.reasons.joinToString(), "where you have worked before")
    }

    @Test
    fun `a slow repository demotes its tasks but never buries them`() {
        val hire = profile(competencies = setOf("kotlin"))
        val slow = StarterWorkMatcher.Responsiveness(medianHoursToFirstResponse = 200, unansweredCount = 3)

        val strongButSlow = StarterWorkMatcher.score(hire, task(competencies = setOf("kotlin")), slow)
        val weakAndFast = StarterWorkMatcher.score(hire, task(competencies = setOf("rust")), null)

        // Demotion, not exclusion: a well-matched task in a slow repo still beats a mismatch.
        assertTrue(strongButSlow.score > weakAndFast.score)
        assertContains(strongButSlow.reasons.joinToString(), "reviews here take")
    }

    @Test
    fun `a repository that has never answered a pull request says so plainly`() {
        val never = StarterWorkMatcher.Responsiveness(medianHoursToFirstResponse = null, unansweredCount = 4)

        val scored = StarterWorkMatcher.score(profile(), task(), never)

        // Not "unknown" -- pull requests exist here and none of them was ever answered.
        assertContains(scored.reasons.joinToString(), "not been getting responses")
        assertTrue(scored.score < 0)
    }

    @Test
    fun `a responsive repository costs nothing and adds no noise`() {
        val fast = StarterWorkMatcher.Responsiveness(medianHoursToFirstResponse = 4, unansweredCount = 0)

        val scored = StarterWorkMatcher.score(profile(competencies = setOf("kotlin")), task(setOf("kotlin")), fast)

        assertTrue(scored.reasons.none { it.startsWith("note:") })
    }

    @Test
    fun `a task nothing matches gets no invented reason`() {
        val scored = StarterWorkMatcher.score(
            profile(competencies = setOf("kotlin")),
            task(competencies = setOf("rust"), type = TaskType.FEATURE),
            null,
        )

        assertEquals(emptyList(), scored.reasons)
        assertEquals(0.0, scored.score)
    }

    @Test
    fun `the same inputs always give the same score`() {
        val hire = profile(competencies = setOf("kotlin"), repositories = setOf("acme/api"))
        val subject = task(competencies = setOf("kotlin"), type = TaskType.BUG)

        assertEquals(
            StarterWorkMatcher.score(hire, subject, null),
            StarterWorkMatcher.score(hire, subject, null),
        )
    }

    /**
     * Review stopped being a gate and became a ranking signal, so what has to hold is that it
     * *only* ranks: an unreviewed task is still suggested, and a good fit still wins.
     */
    @Nested
    inner class UnreviewedTasks {
        @Test
        fun `an unreviewed task ranks below the same task reviewed`() {
            val hire = profile(competencies = setOf("kotlin"))

            val reviewed = StarterWorkMatcher.score(hire, task(competencies = setOf("kotlin")), null)
            val unreviewed =
                StarterWorkMatcher.score(hire, task(competencies = setOf("kotlin"), reviewed = false), null)

            assertTrue(unreviewed.score < reviewed.score)
        }

        @Test
        fun `the demotion carries a sentence a hire can read`() {
            val result = StarterWorkMatcher.score(profile(), task(reviewed = false), null)

            assertContains(result.reasons, "note: nobody has reviewed this task yet")
        }

        @Test
        fun `a reviewed task says nothing about review at all`() {
            val result = StarterWorkMatcher.score(profile(competencies = setOf("kotlin")), task(), null)

            assertTrue(result.reasons.none { reason -> reason.contains("reviewed") })
        }

        /**
         * The property that keeps the demotion a nudge rather than an approval gate: set it any
         * heavier and an unreviewed task never surfaces.
         */
        @Test
        fun `an unreviewed task that fits still beats a reviewed one that does not`() {
            val hire = profile(competencies = setOf("kotlin"))

            val fitsButUnreviewed =
                StarterWorkMatcher.score(hire, task(competencies = setOf("kotlin"), reviewed = false), null)
            val reviewedButUnrelated =
                StarterWorkMatcher.score(hire, task(competencies = setOf("rust")), null)

            assertTrue(fitsButUnreviewed.score > reviewedButUnrelated.score)
        }
    }
}
