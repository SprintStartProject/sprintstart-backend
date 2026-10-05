package com.sprintstart.sprintstartbackend.onboarding.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WrittenOutToolCallTest {
    @Test
    fun `recognises a call written out as text, valid JSON or not`() {
        assertThat("""{"name":"find_member","parameters":{"query":"Ada"}}""".writesOutAToolCall()).isTrue()
        assertThat("""{"name": "open_area", "arguments": {"area": "knowledge"}}""".writesOutAToolCall()).isTrue()
        // Invalid JSON, as the model wrote it.
        val broken = """Let me check. {"name":"find_member","parameters":{"query":""[the name]""}}"""
        assertThat(broken.writesOutAToolCall()).isTrue()
    }

    @Test
    fun `names every tool a reply wrote out, once each`() {
        val written = """{"name":"list_open_escalations","parameters":{}} then """ +
            """{"name":"answer_escalation","arguments":{"request_id":"r1"}} and {"name":"list_open_escalations"}"""

        assertThat(written.writtenOutToolNames()).containsExactly("list_open_escalations", "answer_escalation")
    }

    @Test
    fun `an ordinary answer is not a call, even one that mentions a tool or a name`() {
        assertThat("Ada is waiting on a review; I looked her up with find_member.".writesOutAToolCall()).isFalse()
        assertThat("""Her name is "Ada" and that is all.""".writesOutAToolCall()).isFalse()
        assertThat("""{"name":"Ada"}""".writesOutAToolCall()).isFalse()
        assertThat("".writesOutAToolCall()).isFalse()
    }
}
