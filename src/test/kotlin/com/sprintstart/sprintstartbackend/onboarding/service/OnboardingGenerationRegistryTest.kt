package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.response.path.OnboardingSseEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * A generation outlives the request that started it, and a second request watches it instead of
 * racing it -- the two things that sent hires back to "Start personalization".
 */
class OnboardingGenerationRegistryTest {
    private val personalizationService: OnboardingPersonalizationService = mockk()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val registry = OnboardingGenerationRegistry(personalizationService, mockk(), scope)

    private val authId = "auth|hire"
    private val projectId = UUID.randomUUID()

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a watcher that leaves does not cancel the generation`(): Unit = runBlocking {
        val release = CompletableDeferred<Unit>()
        every { personalizationService.personalize(authId, projectId) } returns flow {
            emit(OnboardingSseEvent(type = "stage", name = "Setup", detail = "Waiting"))
            release.await()
            emit(OnboardingSseEvent(type = "path"))
            emit(OnboardingSseEvent(type = "done"))
        }

        // The first page takes one event and goes away, like a navigation would.
        val firstWatcher = registry.startOrAttach(authId, projectId).first()
        assertThat(firstWatcher.type).isEqualTo("stage")
        assertThat(registry.status(authId)).isNotNull

        release.complete(Unit)
        val later = withTimeout(5_000) { registry.startOrAttach(authId, projectId).toList() }

        assertThat(later.map { it.type }).endsWith("path", "done")
    }

    @Test
    fun `a second request attaches instead of starting another generation`(): Unit = runBlocking {
        val release = CompletableDeferred<Unit>()
        every { personalizationService.personalize(authId, projectId) } returns flow {
            emit(OnboardingSseEvent(type = "stage", name = "Setup", detail = "Waiting"))
            release.await()
            emit(OnboardingSseEvent(type = "done"))
        }

        registry.startOrAttach(authId, projectId).take(1).toList()
        val attached = registry.startOrAttach(authId, projectId)
        release.complete(Unit)

        assertThat(withTimeout(5_000) { attached.toList() }.map { it.type }).containsExactly("stage", "done")
        verify(exactly = 1) { personalizationService.personalize(authId, projectId) }
    }

    @Test
    fun `a page opened halfway through is shown the phases that already finished`(): Unit = runBlocking {
        val release = CompletableDeferred<Unit>()
        every { personalizationService.personalize(authId, projectId) } returns flow {
            emit(OnboardingSseEvent(type = "stage", name = "Setup", detail = "Done"))
            emit(OnboardingSseEvent(type = "stage", name = "Team", detail = "Done"))
            release.await()
            emit(OnboardingSseEvent(type = "done"))
        }

        // The first page sees both stages go by, then leaves.
        registry.startOrAttach(authId, projectId).take(2).toList()
        // A page opened now, before the end, starts from the beginning of the run.
        val late = registry.startOrAttach(authId, projectId)
        release.complete(Unit)

        val seen = withTimeout(5_000) { late.toList() }
        assertThat(seen.map { it.name }).startsWith("Setup", "Team")
        assertThat(seen.last().type).isEqualTo("done")
    }

    @Test
    fun `the run is gone once it has finished`(): Unit = runBlocking {
        every { personalizationService.personalize(authId, projectId) } returns flow {
            emit(OnboardingSseEvent(type = "done"))
        }

        withTimeout(5_000) { registry.startOrAttach(authId, projectId).toList() }
        withTimeout(5_000) {
            while (registry.status(authId) != null) delay(10)
        }

        assertThat(registry.status(authId)).isNull()
    }
}
