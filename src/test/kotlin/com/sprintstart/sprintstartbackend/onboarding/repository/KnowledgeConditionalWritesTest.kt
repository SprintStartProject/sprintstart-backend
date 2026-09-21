package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.KnowledgeRequestStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CanonicalAnswer
import com.sprintstart.sprintstartbackend.onboarding.model.entity.KnowledgeRequest
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

/**
 * The team-mode buddy's knowledge writes rely on their condition being part of the write. Whether a
 * second update really finds nothing to change has to meet a real database.
 */
@ActiveProfiles("test")
@DataJpaTest
// A JPA slice loads no @Configuration of its own, but the entity graph reaches an
// AttributeConverter that needs the encryptor.
@Import(CryptoConfiguration::class)
class KnowledgeConditionalWritesTest {
    @Autowired
    private lateinit var knowledgeRequestRepository: KnowledgeRequestRepository

    @Autowired
    private lateinit var canonicalAnswerRepository: CanonicalAnswerRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private val projectId = UUID.randomUUID()

    private fun openQuestion(): KnowledgeRequest =
        knowledgeRequestRepository.saveAndFlush(
            KnowledgeRequest(projectId = projectId, hireId = UUID.randomUUID(), question = "How do we deploy?"),
        )

    private fun answerIfOpen(id: UUID, onProject: UUID = projectId): Int =
        knowledgeRequestRepository.answerIfOpen(
            id = id,
            projectId = onProject,
            open = KnowledgeRequestStatus.OPEN,
            answered = KnowledgeRequestStatus.ANSWERED,
            answeredBy = UUID.randomUUID(),
            answeredAt = Instant.now(),
            canonicalAnswerId = UUID.randomUUID(),
        )

    /** Answering and dismissing the same question at once: only the first write finds it open. */
    @Test
    fun `a question closes once, and a dismissal after the answer finds nothing open`() {
        val question = openQuestion()

        val answered = answerIfOpen(question.id)
        val dismissedAfter = knowledgeRequestRepository.dismissIfOpen(
            question.id,
            projectId,
            KnowledgeRequestStatus.OPEN,
            KnowledgeRequestStatus.DISMISSED,
        )

        assertThat(answered).isEqualTo(1)
        assertThat(dismissedAfter).isEqualTo(0)
        entityManager.clear()
        assertThat(knowledgeRequestRepository.findById(question.id).orElseThrow().status)
            .isEqualTo(KnowledgeRequestStatus.ANSWERED)
    }

    @Test
    fun `a second answer to the same question changes nothing`() {
        val question = openQuestion()

        assertThat(answerIfOpen(question.id)).isEqualTo(1)
        assertThat(answerIfOpen(question.id)).isEqualTo(0)
    }

    @Test
    fun `a question is never closed from another project`() {
        val question = openQuestion()

        assertThat(answerIfOpen(question.id, onProject = UUID.randomUUID())).isEqualTo(0)
        entityManager.clear()
        assertThat(knowledgeRequestRepository.findById(question.id).orElseThrow().status)
            .isEqualTo(KnowledgeRequestStatus.OPEN)
    }

    /** An edit confirmed from a stale preview must not overwrite the wording written after it. */
    @Test
    fun `an answer is edited only while it is unchanged since it was read`() {
        val author = UUID.randomUUID()
        val stored = canonicalAnswerRepository.saveAndFlush(
            CanonicalAnswer(
                projectId = projectId,
                question = "How do we deploy?",
                answer = "Merge to dev.",
                authorId = author,
            ),
        )
        entityManager.clear()
        val seen = canonicalAnswerRepository.findById(stored.id).orElseThrow().updatedAt

        val first = canonicalAnswerRepository.editIfUnchanged(
            stored.id,
            projectId,
            "How do we deploy?",
            "Tag a release.",
            author,
            Instant.now(),
            seen,
        )
        val stale = canonicalAnswerRepository.editIfUnchanged(
            stored.id,
            projectId,
            "How do we deploy?",
            "Something else.",
            author,
            Instant.now(),
            seen,
        )

        assertThat(first).isEqualTo(1)
        assertThat(stale).isEqualTo(0)
        entityManager.clear()
        assertThat(canonicalAnswerRepository.findById(stored.id).orElseThrow().answer).isEqualTo("Tag a release.")
    }
}
