package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContentProvenance
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyTombstone
import com.sprintstart.sprintstartbackend.onboarding.model.request.competency.CreateCompetencyRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.competency.UpdateCompetencyRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyTombstoneRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for authoring the competency vocabulary.
 *
 * The vocabulary is a flat list of durable names, and two properties matter about it — **the key
 * is identity** (the ledger points at it, so it is slugified on the way in and is never editable),
 * and **removing a competency must not remove what anybody earned**.
 */
class CompetencyGraphAuthoringServiceTest {
    private val competencyRepository: CompetencyRepository = mockk(relaxed = true)

    private val tombstoneRepository: CompetencyTombstoneRepository = mockk(relaxed = true)

    private val service = CompetencyGraphAuthoringService(
        competencyRepository,
        tombstoneRepository,
        CompetencyAreaNormalizer(competencyRepository),
    )

    init {
        // A relaxed mock returns a bare Object from the generic save(S): S, which the checkcast
        // Kotlin inserts at the call site rejects -- echo the argument back instead.
        every { competencyRepository.save(any()) } answers { firstArg() }
        every { tombstoneRepository.save(any()) } answers { firstArg() }
    }

    private fun competency(
        key: String,
        label: String = key,
        kind: CompetencyKind = CompetencyKind.SKILL,
        targetLevel: Int = Competency.DEFAULT_TARGET_LEVEL,
        area: String? = null,
    ) = Competency(key = key, label = label, kind = kind, targetLevel = targetLevel, area = area)

    @Nested
    inner class Reads {
        @Test
        fun `returns the authoring fields, including ones a hire never sees`() {
            every { competencyRepository.findByKey("kotlin") } returns
                competency("kotlin", label = "Kotlin", targetLevel = 3)

            val response = service.getCompetency("kotlin")

            assertEquals("kotlin", response.key)
            assertEquals(3, response.targetLevel)
        }

        @Test
        fun `404s for a competency that does not exist`() {
            every { competencyRepository.findByKey("gone") } returns null

            val error = assertThrows<ResponseStatusException> { service.getCompetency("gone") }

            assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        }

        @Test
        fun `the whole vocabulary is a flat list`() {
            every { competencyRepository.findAll() } returns listOf(competency("git"), competency("kotlin"))

            val graph = service.getGraph()

            assertEquals(listOf("git", "kotlin"), graph.competencies.map { it.key })
        }

        @Test
        fun `an empty vocabulary is an empty response, not a failure`() {
            every { competencyRepository.findAll() } returns emptyList()

            assertEquals(emptyList(), service.getGraph().competencies)
        }
    }

    @Nested
    inner class Creating {
        @Test
        fun `slugifies a hand-typed key into the house style`() {
            every { competencyRepository.findByKey(any()) } returns null

            val response = service.createCompetency(
                CreateCompetencyRequest(
                    key = "  Our Domain Model! ",
                    label = "Our Domain Model",
                    kind = CompetencyKind.CONCEPT,
                ),
            )

            // A hand-authored key must be indistinguishable from a generated one: the ledger keys
            // off it, so two spellings of the same thing would be two different competencies.
            assertEquals("our-domain-model", response.key)
        }

        @Test
        fun `defaults the target level to the intermediate bar when omitted`() {
            every { competencyRepository.findByKey(any()) } returns null

            val response = service.createCompetency(
                CreateCompetencyRequest(key = "kotlin", label = "Kotlin", kind = CompetencyKind.SKILL),
            )

            // Deliberately above beginner: beginner is what the interviewer records when it has the
            // least evidence, so a bar of 1 would make every such placement instant mastery.
            assertEquals(Competency.DEFAULT_TARGET_LEVEL, response.targetLevel)
        }

        @Test
        fun `rejects a key that is blank once slugified`() {
            val error = assertThrows<ResponseStatusException> {
                service.createCompetency(
                    CreateCompetencyRequest(key = "  !!! ", label = "Something", kind = CompetencyKind.SKILL),
                )
            }

            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        }

        @Test
        fun `rejects a blank label`() {
            val error = assertThrows<ResponseStatusException> {
                service.createCompetency(
                    CreateCompetencyRequest(key = "kotlin", label = "   ", kind = CompetencyKind.SKILL),
                )
            }

            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        }

        @Test
        fun `rejects a target level outside 1 to 4`() {
            val error = assertThrows<ResponseStatusException> {
                service.createCompetency(
                    CreateCompetencyRequest(
                        key = "kotlin",
                        label = "Kotlin",
                        kind = CompetencyKind.SKILL,
                        targetLevel = 7,
                    ),
                )
            }

            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        }

        @Test
        fun `409s when the key is already taken`() {
            every { competencyRepository.findByKey("kotlin") } returns competency("kotlin")

            val error = assertThrows<ResponseStatusException> {
                service.createCompetency(
                    CreateCompetencyRequest(key = "kotlin", label = "Kotlin", kind = CompetencyKind.SKILL),
                )
            }

            assertEquals(HttpStatus.CONFLICT, error.statusCode)
        }
    }

    @Nested
    inner class Updating {
        @Test
        fun `applies only the supplied fields and leaves the rest alone`() {
            val existing = competency("kotlin", label = "Kotlin", targetLevel = 2)
            existing.description = "Original"
            every { competencyRepository.findByKey("kotlin") } returns existing

            service.updateCompetency("kotlin", UpdateCompetencyRequest(label = "Kotlin 2.x"))

            assertEquals("Kotlin 2.x", existing.label)
            assertEquals("Original", existing.description)
            assertEquals(2, existing.targetLevel)
        }

        @Test
        fun `a blank description clears it rather than storing whitespace`() {
            val existing = competency("kotlin")
            existing.description = "Original"
            every { competencyRepository.findByKey("kotlin") } returns existing

            service.updateCompetency("kotlin", UpdateCompetencyRequest(description = "   "))

            assertNull(existing.description)
        }

        @Test
        fun `rejects a target level outside 1 to 4`() {
            every { competencyRepository.findByKey("kotlin") } returns competency("kotlin")

            val error = assertThrows<ResponseStatusException> {
                service.updateCompetency("kotlin", UpdateCompetencyRequest(targetLevel = 0))
            }

            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        }

        @Test
        fun `404s for a competency that does not exist`() {
            every { competencyRepository.findByKey("gone") } returns null

            val error = assertThrows<ResponseStatusException> {
                service.updateCompetency("gone", UpdateCompetencyRequest(label = "Anything"))
            }

            assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        }
    }

    @Nested
    inner class Removing {
        /**
         * The property soft removal existed to guarantee, still guaranteed — for a different reason.
         * The ledger is keyed by the competency *key*, not by a foreign key, so deleting the row
         * cannot cascade into anybody's earned progress.
         */
        @Test
        fun `deletes the competency and touches nothing else`() {
            val existing = competency("kotlin")
            every { competencyRepository.findByKey("kotlin") } returns existing

            val response = service.deleteCompetency("kotlin")

            assertEquals("kotlin", response.key)
            verify(exactly = 1) { competencyRepository.delete(existing) }
        }

        @Test
        fun `404s for a key that does not exist`() {
            every { competencyRepository.findByKey("gone") } returns null

            val error = assertThrows<ResponseStatusException> { service.deleteCompetency("gone") }

            assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        }
    }

    /**
     * Grouping is what replaced the graph, so the property that matters is that it does not
     * fragment: an area is only useful if two competencies about the same subject land in one group.
     */
    @Nested
    inner class Area {
        @Test
        fun `reuses the spelling already in use rather than adding a synonym of it`() {
            every { competencyRepository.findDistinctAreas() } returns listOf("Authentication")
            every { competencyRepository.findByKey("session-store") } returns null

            val response = service.createCompetency(
                CreateCompetencyRequest(
                    key = "session-store",
                    label = "Session store",
                    kind = CompetencyKind.SKILL,
                    area = "  authentication ",
                ),
            )

            assertEquals("Authentication", response.area)
        }

        @Test
        fun `keeps an area nothing matches, so a new subject can still be named`() {
            every { competencyRepository.findDistinctAreas() } returns listOf("Authentication")
            every { competencyRepository.findByKey("chunking") } returns null

            val response = service.createCompetency(
                CreateCompetencyRequest(
                    key = "chunking",
                    label = "Chunking",
                    kind = CompetencyKind.SKILL,
                    area = " Ingestion ",
                ),
            )

            assertEquals("Ingestion", response.area)
        }

        @Test
        fun `a blank area clears the grouping instead of storing an empty one`() {
            every { competencyRepository.findDistinctAreas() } returns listOf("Authentication")
            val existing = competency("jwt", area = "Authentication")
            every { competencyRepository.findByKey("jwt") } returns existing

            val response = service.updateCompetency("jwt", UpdateCompetencyRequest(area = "  "))

            assertNull(response.area)
        }

        @Test
        fun `an omitted area leaves the grouping alone`() {
            val existing = competency("jwt", area = "Authentication")
            every { competencyRepository.findByKey("jwt") } returns existing

            val response = service.updateCompetency("jwt", UpdateCompetencyRequest(label = "JWT"))

            assertEquals("Authentication", response.area)
        }
    }

    /**
     * The two protections that must exist before generation runs on ingestion: without them,
     * turning it on discards a PM's edits and re-proposes what they deleted, forever.
     */
    @Nested
    inner class ProvenanceAndTombstones {
        @Test
        fun `a hand-authored competency is a person's row`() {
            every { competencyRepository.findByKey("kotlin") } returns null

            service.createCompetency(
                CreateCompetencyRequest(key = "kotlin", label = "Kotlin", kind = CompetencyKind.SKILL),
            )

            val saved = slot<Competency>()
            verify { competencyRepository.save(capture(saved)) }
            assertEquals(ContentProvenance.PM, saved.captured.provenance)
        }

        @Test
        fun `editing an AI-written competency makes it a person's row`() {
            val existing = competency("kotlin").apply { provenance = ContentProvenance.AI }
            every { competencyRepository.findByKey("kotlin") } returns existing

            service.updateCompetency("kotlin", UpdateCompetencyRequest(label = "Kotlin 2"))

            assertEquals(ContentProvenance.PM, existing.provenance)
        }

        @Test
        fun `removing a competency remembers it, by name as well as key`() {
            val existing = competency("kotlin", label = "Kotlin")
            every { competencyRepository.findByKey("kotlin") } returns existing
            every { tombstoneRepository.findByKey("kotlin") } returns null

            service.deleteCompetency("kotlin")

            val tombstone = slot<CompetencyTombstone>()
            verify { tombstoneRepository.save(capture(tombstone)) }
            assertEquals("kotlin", tombstone.captured.key)
            // The label is what blocks a rephrasing; the key alone would not.
            assertEquals("Kotlin", tombstone.captured.label)
        }

        @Test
        fun `re-deleting a key that was added back refreshes the tombstone instead of failing`() {
            val existing = competency("kotlin", label = "Kotlin again")
            every { competencyRepository.findByKey("kotlin") } returns existing
            val previous = CompetencyTombstone(key = "kotlin", label = "Kotlin")
            every { tombstoneRepository.findByKey("kotlin") } returns previous

            service.deleteCompetency("kotlin")

            assertEquals("Kotlin again", previous.label)
            verify(exactly = 0) { tombstoneRepository.save(any()) }
        }

        @Test
        fun `hand-authoring a removed key again clears the tombstone`() {
            every { competencyRepository.findByKey("kotlin") } returns null

            service.createCompetency(
                CreateCompetencyRequest(key = "kotlin", label = "Kotlin", kind = CompetencyKind.SKILL),
            )

            // The tombstone binds the generator, never the person who changed their mind.
            verify(exactly = 1) { tombstoneRepository.deleteByKey("kotlin") }
        }
    }
}
