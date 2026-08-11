package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.ProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubProjectAccessGuardTest {
    private val userApi = mockk<UserApi>()
    private val guard = GithubProjectAccessGuard(userApi)

    @Test
    fun `allows source linking when caller has project access`() {
        val projectId = UUID.randomUUID()
        every { userApi.userHasAccessToProject("auth-id", projectId) } returns true

        assertThatCode { guard.requireProjectAccess("auth-id", projectId) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `denies source linking without revealing source state`() {
        val projectId = UUID.randomUUID()
        every { userApi.userHasAccessToProject("auth-id", projectId) } returns false

        assertThatThrownBy { guard.requireProjectAccess("auth-id", projectId) }
            .isInstanceOf(ProjectAccessDeniedException::class.java)
            .hasMessage("No access to project with id $projectId")
    }
}
