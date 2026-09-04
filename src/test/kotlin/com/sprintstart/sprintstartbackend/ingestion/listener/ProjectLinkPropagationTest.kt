package com.sprintstart.sprintstartbackend.ingestion.listener

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.connectors.github.external.events.projects.GithubRepositoryProjectLinkChangedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.projects.JiraInstanceProjectLinkChangedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSourceRef
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactProjectService
import io.mockk.coJustRun
import io.mockk.coVerify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * Both link listeners run `AFTER_COMMIT`, and a plain `@TransactionalEventListener` drops its event
 * when nothing is committing.
 *
 * That is not a hypothetical here. The connect paths that announce these links -- GitHub's
 * `connectRepositoryIfNecessary` and Jira's `connectInstanceIfNeeded` -- are `@Transactional`
 * *suspending* functions, and Spring opens no transaction for those at all: the annotation needs a
 * `ReactiveTransactionManager` to apply to a suspending function, and this application has a
 * JPA `PlatformTransactionManager`. So the whole propagation was announced into the void, and
 * linking an already-connected source to a project left its content unreachable there -- the exact
 * failure this feature exists to fix.
 *
 * `fallbackExecution = true` is what makes the listener run anyway. This test pins that down by
 * publishing with no transaction in sight, which is what those paths do.
 */
@ActiveProfiles("test")
@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:linkpropagation;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    ],
)
class ProjectLinkPropagationTest {
    @MockkBean
    private lateinit var artifactProjectService: ArtifactProjectService

    @Autowired
    private lateinit var eventPublisher: ApplicationEventPublisher

    private val projectId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        coJustRun { artifactProjectService.applyProjectLink(any(), any(), any()) }
    }

    @Test
    fun `a repository link announced outside a transaction still reaches the artifacts`() {
        eventPublisher.publishEvent(
            GithubRepositoryProjectLinkChangedEvent(
                owner = "acme",
                name = "repo",
                projectId = projectId,
                linked = true,
            ),
        )

        coVerify(timeout = LISTENER_TIMEOUT_MS) {
            artifactProjectService.applyProjectLink(
                ArtifactSourceRef.GithubRepository("acme", "repo"),
                projectId,
                true,
            )
        }
    }

    @Test
    fun `a repository unlink announced outside a transaction still reaches the artifacts`() {
        eventPublisher.publishEvent(
            GithubRepositoryProjectLinkChangedEvent(
                owner = "acme",
                name = "repo",
                projectId = projectId,
                linked = false,
            ),
        )

        coVerify(timeout = LISTENER_TIMEOUT_MS) {
            artifactProjectService.applyProjectLink(
                ArtifactSourceRef.GithubRepository("acme", "repo"),
                projectId,
                false,
            )
        }
    }

    @Test
    fun `a Jira instance link announced outside a transaction still reaches the artifacts`() {
        eventPublisher.publishEvent(
            JiraInstanceProjectLinkChangedEvent(
                instanceUrl = "https://acme.atlassian.net",
                projectId = projectId,
                linked = true,
            ),
        )

        coVerify(timeout = LISTENER_TIMEOUT_MS) {
            artifactProjectService.applyProjectLink(
                ArtifactSourceRef.JiraInstance("https://acme.atlassian.net"),
                projectId,
                true,
            )
        }
    }

    private companion object {
        // The listeners hand the work to `applicationScope`, so the call lands on another thread.
        const val LISTENER_TIMEOUT_MS = 5_000L
    }
}
