package com.sprintstart.sprintstartbackend.offboarding.scoring

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class AuthorshipTest {
    private fun authors(file: FileHistory, profile: ScoringProfile = ScoringProfile()) =
        Authorship(profile, NOW).authorsOf(file).map { it.value }

    @Test
    fun `the only person who changed a file is its author`() {
        assertThat(authors(history("a.kt", "ada@x.org" to 400))).containsExactly("ada@x.org")
    }

    @Test
    fun `a creator who keeps maintaining a file is its only author next to a drive-by`() {
        val file = history(
            "a.kt",
            "ada@x.org" to 400,
            "ada@x.org" to 30,
            "ada@x.org" to 25,
            "ada@x.org" to 20,
            "ben@x.org" to 15,
        )

        assertThat(authors(file)).containsExactly("ada@x.org")
    }

    @Test
    fun `two people who maintain a file equally are both authors`() {
        val file = history(
            "a.kt",
            *repeated("ada@x.org", 5, 60),
            *repeated("ben@x.org", 5, 30),
        )

        assertThat(authors(file)).containsExactlyInAnyOrder("ada@x.org", "ben@x.org")
    }

    @Test
    fun `a maintainer from long ago stops counting once old changes fade`() {
        val file = history(
            "a.kt",
            "cy@x.org" to 1_200,
            *repeated("xia@x.org", 5, 1_100),
            *repeated("yan@x.org", 5, 20),
        )
        val noFading = ScoringProfile(halfLifeDays = 1_000_000.0)

        assertThat(authors(file, noFading)).contains("xia@x.org")
        assertThat(authors(file)).doesNotContain("xia@x.org").contains("yan@x.org")
    }

    @Test
    fun `a creator keeps their bonus when every change has faded`() {
        val file = history("a.kt", "ada@x.org" to 3_000, "ben@x.org" to 2_990)

        assertThat(authors(file)).contains("ada@x.org")
    }

    @Test
    fun `a change made now counts fully`() {
        assertThat(Authorship(ScoringProfile(), NOW).weight(Touch(PersonKey("a"), NOW))).isEqualTo(1.0)
    }

    @Test
    fun `a change counts half after one half-life`() {
        val profile = ScoringProfile(halfLifeDays = 100.0)

        assertThat(Authorship(profile, NOW).weight(touch("a@x.org", 100))).isEqualTo(0.5, within(1e-9))
    }

    @Test
    fun `a change dated after the date counts fully instead of growing`() {
        assertThat(Authorship(ScoringProfile(), NOW).weight(touch("a@x.org", -30))).isEqualTo(1.0)
    }

    @Test
    fun `a stricter threshold drops a maintainer whose degree is slightly below the creator's`() {
        val file = history("a.kt", "ada@x.org" to 10, *repeated("ben@x.org", 5, 10))

        assertThat(authors(file)).containsExactlyInAnyOrder("ada@x.org", "ben@x.org")
        assertThat(authors(file, ScoringProfile(authorThreshold = 0.99))).containsExactly("ada@x.org")
    }
}
