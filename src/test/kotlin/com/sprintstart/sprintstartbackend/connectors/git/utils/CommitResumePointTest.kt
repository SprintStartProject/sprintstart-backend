package com.sprintstart.sprintstartbackend.connectors.git.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CommitResumePointTest {
    @Test
    fun `resumes from the stored cursor once the commits carry their files`() {
        assertThat(commitResumePoint("abc123", filesRecorded = true)).isEqualTo("abc123")
    }

    @Test
    fun `reads from the beginning while the commits carry no files`() {
        assertThat(commitResumePoint("abc123", filesRecorded = false)).isEmpty()
    }

    @Test
    fun `reads from the beginning for a repository that was never ingested`() {
        assertThat(commitResumePoint("", filesRecorded = false)).isEmpty()
        assertThat(commitResumePoint("", filesRecorded = true)).isEmpty()
    }
}
