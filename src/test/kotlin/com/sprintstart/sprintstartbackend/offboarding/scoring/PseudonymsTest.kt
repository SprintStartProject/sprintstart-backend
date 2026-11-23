package com.sprintstart.sprintstartbackend.offboarding.scoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PseudonymsTest {
    private val key = Pseudonyms.keyFrom("a secret")

    @Test
    fun `a pseudonym is a prefix and twelve hexadecimal digits that hide the identity`() {
        val pseudonym = Pseudonyms(key, "project-1").of("ada@x.org")

        assertThat(pseudonym).matches("P-[0-9a-f]{12}")
        assertThat(pseudonym).doesNotContain("ada").doesNotContain("x.org")
    }

    @Test
    fun `the same identity always gets the same pseudonym`() {
        assertThat(Pseudonyms(key, "project-1").of("ada@x.org"))
            .isEqualTo(Pseudonyms(Pseudonyms.keyFrom("a secret"), "project-1").of("ada@x.org"))
    }

    @Test
    fun `different identities get different pseudonyms`() {
        val names = Pseudonyms(key, "project-1")

        assertThat(listOf("ada@x.org", "ada", "ben@x.org", "ben", "Ada@x.org").map(names::of)).doesNotHaveDuplicates()
    }

    @Test
    fun `the same identity has another pseudonym in another scope`() {
        assertThat(Pseudonyms(key, "project-1").of("ada")).isNotEqualTo(Pseudonyms(key, "project-2").of("ada"))
    }

    @Test
    fun `the same identity has another pseudonym under another secret`() {
        val other = Pseudonyms.keyFrom("another secret")

        assertThat(Pseudonyms(key, "project-1").of("ada")).isNotEqualTo(Pseudonyms(other, "project-1").of("ada"))
    }

    @Test
    fun `the key is derived from the secret and is not the secret`() {
        assertThat(key).hasSize(32)
        assertThat(String(key, Charsets.ISO_8859_1)).doesNotContain("a secret")
        assertThat(Pseudonyms.keyFrom("a secret")).isEqualTo(key)
    }

    @Test
    fun `the scope and the identity cannot be shifted into each other`() {
        assertThat(Pseudonyms(key, "ab").of("c")).isNotEqualTo(Pseudonyms(key, "a").of("bc"))
    }
}
