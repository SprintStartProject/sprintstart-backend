package com.sprintstart.sprintstartbackend.offboarding.scoring

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PersonKeyTest {
    @Test
    fun `a no-reply address with an account id is reduced to the login`() {
        assertThat(PersonKey.ofEmail("12345+Ada@users.noreply.github.com")).isEqualTo(PersonKey("ada"))
    }

    @Test
    fun `a no-reply address without an account id is reduced to the login`() {
        assertThat(PersonKey.ofEmail("ada@users.noreply.github.com")).isEqualTo(PersonKey("ada"))
    }

    @Test
    fun `the same person under a no-reply address and a login is one person`() {
        assertThat(PersonKey.ofEmail("1+ada@users.noreply.github.com")).isEqualTo(PersonKey.parse("Ada"))
    }

    @Test
    fun `any other email stands for itself in lower case`() {
        assertThat(PersonKey.ofEmail("  Ada@Example.COM ")).isEqualTo(PersonKey("ada@example.com"))
    }

    @Test
    fun `an address that only ends like a no-reply address is not reduced`() {
        assertThat(PersonKey.ofEmail("ada@users.noreply.github.com.evil.org").value)
            .isEqualTo("ada@users.noreply.github.com.evil.org")
    }

    @Test
    fun `a typed email is read like a commit email`() {
        assertThat(PersonKey.parse("Ada@Example.com")).isEqualTo(PersonKey("ada@example.com"))
    }

    @Test
    fun `a typed login is trimmed and lower-cased`() {
        assertThat(PersonKey.parse("  ADA ")).isEqualTo(PersonKey("ada"))
    }

    @Test
    fun `a blank person is rejected`() {
        assertThatThrownBy { PersonKey.parse("   ") }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
