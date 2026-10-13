package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardActor
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardChange
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCard
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * A card is written whole, so two requests that read the same state would each overwrite the other.
 * The version check is what makes the second one fail instead; a mocked repository cannot show that,
 * only a real UPDATE with its version condition can.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import(CryptoConfiguration::class)
class BoardCardVersionTest {
    @Autowired
    private lateinit var repository: BoardCardRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private fun card(payload: String = "B") =
        BoardCard(
            boardId = UUID.randomUUID(),
            kind = BoardCardKind.NOTE,
            owner = BoardCardOwner.HIRE,
            position = 0,
            payload = payload,
        )

    private fun edit(card: BoardCard, to: String, by: BoardActor = BoardActor.HIRE) =
        card.replacePayload(to, BoardCardChange.EDITED, by)

    /** Undo read the card at B/A; a hire edit to C committed; undo must not commit over it. */
    @Test
    fun `a write from a stale read fails instead of overwriting a newer committed edit`() {
        val stored = repository.saveAndFlush(card("A"))
        edit(stored, "B")
        repository.saveAndFlush(stored)
        entityManager.clear()

        val undoRead = repository.findById(stored.id).orElseThrow()
        entityManager.detach(undoRead)
        val editRead = repository.findById(stored.id).orElseThrow()
        edit(editRead, "C")
        repository.saveAndFlush(editRead)
        entityManager.clear()

        edit(undoRead, "A")

        assertThatThrownBy { repository.saveAndFlush(undoRead) }
            .isInstanceOf(OptimisticLockingFailureException::class.java)
        entityManager.clear()
        val persisted = repository.findById(stored.id).orElseThrow()
        assertThat(persisted.payload).isEqualTo("C")
        assertThat(persisted.previousPayload).isEqualTo("B")
    }

    @Test
    fun `a write that only reorders the card still loses to a committed content edit`() {
        val stored = repository.saveAndFlush(card())
        entityManager.clear()
        val reorderRead = repository.findById(stored.id).orElseThrow()
        entityManager.detach(reorderRead)
        val editRead = repository.findById(stored.id).orElseThrow()
        edit(editRead, "C")
        repository.saveAndFlush(editRead)
        entityManager.clear()

        reorderRead.position = 5

        assertThatThrownBy { repository.saveAndFlush(reorderRead) }
            .isInstanceOf(OptimisticLockingFailureException::class.java)
    }

    /** The revision is the undo token; it survives the database and moves only with the content. */
    @Test
    fun `the content revision is stored and counts each real change`() {
        val stored = repository.saveAndFlush(card())
        edit(stored, "C")
        edit(stored, "C")
        edit(stored, "D")
        repository.saveAndFlush(stored)
        entityManager.clear()

        val loaded = repository.findById(stored.id).orElseThrow()

        assertThat(loaded.contentRevision).isEqualTo(2)
    }
}
