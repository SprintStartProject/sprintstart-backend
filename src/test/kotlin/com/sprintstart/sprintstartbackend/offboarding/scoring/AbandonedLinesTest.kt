package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileAuthorshipReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileComplexityReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileReview
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.PageEditReading
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.Instant

class AbandonedLinesTest {
    private val ada = PersonKey("ada@x.org")
    private val ben = PersonKey("ben@x.org")
    private val file = FileKey(REPOSITORY, "core.kt")

    private fun risk(history: FileHistory, leaving: PersonKey, authorship: Map<PersonKey, Int>?) =
        RiskScorer()
            .score(
                listOf(history),
                DepartureQuery(setOf(leaving), NOW),
                FileFacts(authorship = authorship?.let { mapOf(file to it) } ?: emptyMap()),
            ).files
            .single()

    @Test
    fun `the share is the lines of the leaving people over all the lines`() {
        val core = history("core.kt", "ada@x.org" to 30, "ben@x.org" to 20)

        val risk = risk(core, ada, mapOf(ada to 8, ben to 2))

        assertThat(risk.abandonedShare).isCloseTo(0.8, within(1e-9))
    }

    @Test
    fun `lines of people who have been inactive for long count as left`() {
        val core = history("core.kt", "ada@x.org" to 30, "ben@x.org" to 500, "cy@x.org" to 400)

        val risk = risk(core, ada, mapOf(ada to 5, ben to 3, PersonKey("cy@x.org") to 2))

        assertThat(risk.abandonedShare).isCloseTo(1.0, within(1e-9))
    }

    @Test
    fun `a file nobody blamed has no share`() {
        val core = history("core.kt", "ada@x.org" to 30)

        assertThat(risk(core, ada, null).abandonedShare).isNull()
        assertThat(risk(core, ada, emptyMap()).abandonedShare).isNull()
    }

    @Test
    fun `lines nobody who left wrote make a share of nothing`() {
        val core = history("core.kt", "ada@x.org" to 30, "ben@x.org" to 20)

        assertThat(risk(core, ada, mapOf(ben to 4)).abandonedShare).isEqualTo(0.0)
    }

    @Test
    fun `the authors of lines are the people the confirmed identities say they are`() {
        val api = object : ContributionEvidenceApi {
            override fun fileContributions(projectId: ProjectId, asOf: Instant): List<FileContribution> = emptyList()

            override fun fileReviews(projectId: ProjectId, asOf: Instant): List<FileReview> = emptyList()

            override fun pageEdits(projectId: ProjectId, asOf: Instant): List<PageEditReading> = emptyList()

            override fun fileComplexities(repositories: Collection<String>): List<FileComplexityReading> = emptyList()

            override fun fileAuthorship(repositories: Collection<String>) =
                listOf(
                    FileAuthorshipReading(REPOSITORY, "core.kt", "ada@x.org", 6),
                    FileAuthorshipReading(REPOSITORY, "core.kt", "ada@old.org", 2),
                    FileAuthorshipReading(REPOSITORY, "core.kt", "ben@x.org", 2),
                )
        }
        val identities = Identities(listOf(AliasLink(PersonKey("ada@old.org"), ada)))

        val authorship = FileFactsReader(api).of(listOf(REPOSITORY), identities).authorship

        assertThat(authorship).isEqualTo(mapOf(file to mapOf(ada to 8, ben to 2)))
    }
}
