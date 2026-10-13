package com.sprintstart.sprintstartbackend.connectors.git.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils.BitbucketSourceUrls
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BitbucketSourceUrlsTest {
    private val urls = BitbucketSourceUrls()

    @Test
    fun `builds the repository url`() {
        assertThat(urls.repositoryUrl(listOf("sprintstart"), "sprintstart-backend"))
            .isEqualTo("https://bitbucket.org/sprintstart/sprintstart-backend")
    }

    @Test
    fun `builds a file url under src at the ingested revision`() {
        assertThat(urls.fileUrl(listOf("sprintstart"), "sprintstart-backend", "abc123", "src/Main.kt"))
            .isEqualTo("https://bitbucket.org/sprintstart/sprintstart-backend/src/abc123/src/Main.kt")
    }

    @Test
    fun `builds a commit url under commits`() {
        assertThat(urls.commitUrl(listOf("sprintstart"), "sprintstart-backend", "abc123"))
            .isEqualTo("https://bitbucket.org/sprintstart/sprintstart-backend/commits/abc123")
    }

    @Test
    fun `supports a nested namespace`() {
        assertThat(urls.repositoryUrl(listOf("group", "subgroup"), "project"))
            .isEqualTo("https://bitbucket.org/group/subgroup/project")
    }
}
