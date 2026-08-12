package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyCompactRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyCompactResponse
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyMessage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.util.Optional
import java.util.UUID

/**
 * Compaction's own rules, several of them re-homed from `BuddyServiceTest` when folding moved off
 * the agent turn.
 *
 * ⚠️ Deleting a test along with its subject is how a rule stops being checked without anybody
 * deciding it should — and the diff looks like pure subtraction either way. What the turn used to
 * pin ("fold once the window outgrows the limit", "the returned note and the advanced cursor are
 * persisted together") is pinned here instead.
 */
class BuddyCompactionServiceTest {
    private val buddySessionRepository: BuddySessionRepository = mockk()
    private val buddyMessageRepository: BuddyMessageRepository = mockk()
    private val onboardingAiClient: OnboardingAiClient = mockk()

    // Runs the callback inline; these tests are about what the fold decides, not about JPA.
    private val transactionManager: PlatformTransactionManager = mockk {
        every { getTransaction(any()) } returns SimpleTransactionStatus()
        every { commit(any<TransactionStatus>()) } returns Unit
        every { rollback(any<TransactionStatus>()) } returns Unit
    }

    private val service = BuddyCompactionService(
        buddySessionRepository,
        buddyMessageRepository,
        onboardingAiClient,
        transactionManager,
    )

    private val userId = UUID.randomUUID()

    private fun sessionWith(messageCount: Int, cursor: Int = 0, summary: String? = null): BuddySession {
        val session = BuddySession(userId = userId, summary = summary, summarizedCount = cursor)
        every { buddySessionRepository.findByUserId(userId) } returns session
        every { buddySessionRepository.findById(session.id) } returns Optional.of(session)
        every { buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id) } returns
            (1..messageCount).map {
                BuddyMessage(
                    session = session,
                    role = if (it % 2 == 1) BuddyMessageRole.USER else BuddyMessageRole.ASSISTANT,
                    content = "m$it",
                )
            }
        return session
    }

    @Test
    fun `folds the oldest messages and advances the cursor by exactly what it sent`() = runTest {
        val session = sessionWith(messageCount = 26, summary = "Earlier notes.")
        every { buddySessionRepository.save(any()) } answers { firstArg() }
        val requests = mutableListOf<BuddyCompactRequest>()
        coEvery { onboardingAiClient.compactBuddyMemory(capture(requests)) } returns
            BuddyCompactResponse(memory = "We covered m1 through m6.")

        service.compactIfNeeded(userId)

        // 26 messages, window 20: the six oldest slide out.
        assertThat(requests.single().folded.map { it.content })
            .containsExactly("m1", "m2", "m3", "m4", "m5", "m6")
        assertThat(requests.single().priorSummary).isEqualTo("Earlier notes.")
        assertThat(session.summary).isEqualTo("We covered m1 through m6.")
        assertThat(session.summarizedCount).isEqualTo(6)
    }

    @Test
    fun `does nothing and calls no model when the window still fits`() = runTest {
        sessionWith(messageCount = 20)

        service.compactIfNeeded(userId)

        coVerify(exactly = 0) { onboardingAiClient.compactBuddyMemory(any()) }
        verify(exactly = 0) { buddySessionRepository.save(any()) }
    }

    /**
     * ⚠️ Catches up in one call rather than one exchange at a time. A pass that missed its turn
     * (the model was down, the process restarted) would otherwise never close the gap, since each
     * turn adds two messages and a fixed-size fold removes two.
     */
    @Test
    fun `folds the whole backlog at once, not one exchange`() = runTest {
        sessionWith(messageCount = 60)
        every { buddySessionRepository.save(any()) } answers { firstArg() }
        val requests = mutableListOf<BuddyCompactRequest>()
        coEvery { onboardingAiClient.compactBuddyMemory(capture(requests)) } returns
            BuddyCompactResponse(memory = "note")

        service.compactIfNeeded(userId)

        assertThat(requests.single().folded).hasSize(40)
    }

    @Test
    fun `starts from the cursor, never from the top of the transcript`() = runTest {
        sessionWith(messageCount = 30, cursor = 5)
        every { buddySessionRepository.save(any()) } answers { firstArg() }
        val requests = mutableListOf<BuddyCompactRequest>()
        coEvery { onboardingAiClient.compactBuddyMemory(capture(requests)) } returns
            BuddyCompactResponse(memory = "note")

        service.compactIfNeeded(userId)

        // 30 messages, 5 already covered, window 20: five more slide out, starting at m6.
        assertThat(requests.single().folded.map { it.content })
            .containsExactly("m6", "m7", "m8", "m9", "m10")
    }

    /**
     * ⚠️ An unavailable model leaves the cursor exactly where it was. Advancing it past messages
     * nothing summarized is the one way this design loses a transcript — the note would claim to
     * cover turns it had never seen, and the window would no longer send them either.
     */
    @Test
    fun `leaves the cursor alone when the model is unavailable`() = runTest {
        val session = sessionWith(messageCount = 26, summary = "Earlier notes.")
        coEvery { onboardingAiClient.compactBuddyMemory(any()) } throws
            OnboardingAiException(503, "model down", "unavailable")

        service.compactIfNeeded(userId)

        assertThat(session.summarizedCount).isEqualTo(0)
        assertThat(session.summary).isEqualTo("Earlier notes.")
        verify(exactly = 0) { buddySessionRepository.save(any()) }
    }

    /** Never throws: the caller is a fire-and-forget launch behind a hire's reply. */
    @Test
    fun `swallows an AI failure rather than surfacing it`() = runTest {
        sessionWith(messageCount = 26)
        coEvery { onboardingAiClient.compactBuddyMemory(any()) } throws
            OnboardingAiException(500, "boom", "exploded")

        service.compactIfNeeded(userId)
    }

    /**
     * ⚠️ The first of the two races: a fold committed while this one's model call was in flight.
     * Applying this fold anyway would move the cursor past messages the *other* note does not
     * cover.
     */
    @Test
    fun `discards its fold when the cursor moved while the model was thinking`() = runTest {
        val session = sessionWith(messageCount = 26)
        coEvery { onboardingAiClient.compactBuddyMemory(any()) } coAnswers {
            // Somebody else's fold lands mid-call.
            session.summarizedCount = 6
            session.summary = "somebody else's note"
            BuddyCompactResponse(memory = "my note")
        }

        service.compactIfNeeded(userId)

        assertThat(session.summary).isEqualTo("somebody else's note")
        assertThat(session.summarizedCount).isEqualTo(6)
        verify(exactly = 0) { buddySessionRepository.save(any()) }
    }

    /**
     * ⚠️ The second race, and why the entity carries a `@Version` at all: a competing fold can
     * commit *between* the re-read above and the flush, which no re-check can see.
     * `backend#170` is the local precedent — narrowing that window is not closing it.
     */
    @Test
    fun `survives losing the optimistic lock on the swap`() = runTest {
        sessionWith(messageCount = 26)
        coEvery { onboardingAiClient.compactBuddyMemory(any()) } returns
            BuddyCompactResponse(memory = "my note")
        every { buddySessionRepository.save(any()) } throws
            ObjectOptimisticLockingFailureException(BuddySession::class.java, UUID.randomUUID())

        service.compactIfNeeded(userId)
    }

    @Test
    fun `does nothing when the user has no session`() = runTest {
        every { buddySessionRepository.findByUserId(userId) } returns null

        service.compactIfNeeded(userId)

        coVerify(exactly = 0) { onboardingAiClient.compactBuddyMemory(any()) }
    }
}
