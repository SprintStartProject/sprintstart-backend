package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.PageEditReading
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PageEditEvidenceTest {
    private val project = ProjectId(UUID.randomUUID())
    private val api = mockk<ContributionEvidenceApi>()
    private val source = PageEditEvidence(api)

    private fun edit(path: String, author: String, day: Long) =
        PageEditReading("confluence:ENG", path, author, NOW.minusSeconds(day * 86_400))

    @Test
    fun `the versions of a page become one history credited to atlassian accounts`() {
        every { api.pageEdits(project, NOW) } returns
            listOf(edit("Setup", "Acc-1", 9), edit("Runbook", "acc-2", 5), edit("Setup", "acc-2", 3))

        val histories = source.histories(project, NOW).associateBy { it.path }

        assertThat(histories.keys).containsExactlyInAnyOrder("Setup", "Runbook")
        val setup = histories.getValue("Setup")
        assertThat(setup.repository).isEqualTo("confluence:ENG")
        assertThat(setup.touches.map { it.person.value }).containsExactly("atlassian:acc-1", "atlassian:acc-2")
        assertThat(setup.touches).allMatch { it.source == SourceId.PAGE_EDITS && it.founding }
    }

    @Test
    fun `a project without page history has no page evidence`() {
        every { api.pageEdits(project, any<Instant>()) } returns emptyList()

        assertThat(source.histories(project, NOW)).isEmpty()
    }

    @Test
    fun `a page somebody wrote alone is at risk when that account is linked to the leaving person`() {
        every { api.pageEdits(project, NOW) } returns listOf(edit("Setup", "acc-1", 9), edit("Setup", "acc-1", 2))
        val identities = Identities(listOf(AliasLink(PersonKey.ofAtlassian("acc-1"), PersonKey("ada"))))
        val histories = source.histories(project, NOW).map(identities::resolve)
        val profile = WeightProfile(sourceWeights = mapOf(SourceId.PAGE_EDITS to 1.0))

        val map = RiskScorer().score(histories, DepartureQuery(setOf(PersonKey("ada")), NOW, profile))

        assertThat(map.files.single().path).isEqualTo("Setup")
        assertThat(map.files.single().level).isEqualTo(RiskLevel.ORPHANED)
    }

    @Test
    fun `a notion page is credited to notion users`() {
        every { api.pageEdits(project, NOW) } returns
            listOf(PageEditReading("notion:Acme", "Runbook", "U-1", NOW.minusSeconds(60)))

        val touch = source
            .histories(project, NOW)
            .single()
            .touches
            .single()

        assertThat(touch.person.value).isEqualTo("notion:u-1")
    }
}
