package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileAuthorshipReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileComplexityReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileKind
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileReview
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.PageEditReading
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.Instant

class HotspotsTest {
    private fun key(path: String) = FileKey(REPOSITORY, path)

    private fun history(path: String) = FileHistory(REPOSITORY, path, listOf(touch("ada@x.org", 10)))

    private val busyAndComplex = history("hot.kt")
    private val busyAndSimple = history("busy.kt")
    private val quietAndComplex = history("complex.kt")
    private val quietAndSimple = history("calm.kt")

    private val activities = mapOf(
        key("hot.kt") to 10.0,
        key("busy.kt") to 9.0,
        key("complex.kt") to 2.0,
        key("calm.kt") to 1.0,
    )

    private fun code(value: Double) = Complexity(FileKind.CODE, value)

    private val complexities = mapOf(
        key("hot.kt") to code(90.0),
        key("busy.kt") to code(5.0),
        key("complex.kt") to code(80.0),
        key("calm.kt") to code(1.0),
    )
    private val hotspots = Hotspots(activities, complexities)

    @Test
    fun `a file that is complicated and changed often is the biggest hotspot`() {
        val ranked = listOf(busyAndComplex, busyAndSimple, quietAndComplex, quietAndSimple)
            .map { it.path to hotspots.of(it)!! }
            .sortedByDescending { it.second }

        assertThat(ranked.first().first).isEqualTo("hot.kt")
        assertThat(ranked.last().first).isEqualTo("calm.kt")
    }

    @Test
    fun `a file that is only busy or only complicated is no hotspot`() {
        val hot = hotspots.of(busyAndComplex)!!

        assertThat(hotspots.of(busyAndSimple)!!).isLessThan(hot / 2)
        assertThat(hotspots.of(quietAndComplex)!!).isLessThan(hot / 2)
    }

    @Test
    fun `the hotspot is the product of two percentile ranks`() {
        assertThat(hotspots.of(busyAndComplex)!!).isCloseTo((3.5 / 4) * (3.5 / 4), within(1e-9))
        assertThat(hotspots.of(quietAndSimple)!!).isCloseTo((0.5 / 4) * (0.5 / 4), within(1e-9))
    }

    @Test
    fun `files that tie share the middle of their rank`() {
        val same =
            Hotspots(
                mapOf(key("a.kt") to 1.0, key("b.kt") to 1.0),
                mapOf(
                    key("a.kt") to code(2.0),
                    key("b.kt") to code(2.0),
                ),
            )

        assertThat(same.of(history("a.kt"))!!).isCloseTo(0.25, within(1e-9))
    }

    @Test
    fun `a file whose complexity was never measured has no hotspot`() {
        val partial = Hotspots(activities, complexities - key("hot.kt"))

        assertThat(partial.of(busyAndComplex)).isNull()
        assertThat(partial.of(busyAndSimple)).isNotNull()
    }

    @Test
    fun `without any complexity nothing is a hotspot`() {
        assertThat(Hotspots(activities, emptyMap()).of(busyAndComplex)).isNull()
    }

    @Test
    fun `a ranking carries the hotspot of the files at risk when complexity is given`() {
        val departure = DepartureQuery(setOf(PersonKey("ada@x.org")), NOW)

        val with = RiskScorer()
            .score(
                listOf(busyAndComplex),
                departure,
                FileFacts(mapOf(key("hot.kt") to Complexity(FileKind.PROSE, 50.0))),
            ).files
            .single()
        val without = RiskScorer().score(listOf(busyAndComplex), departure).files.single()

        assertThat(with.hotspot).isNotNull()
        assertThat(with.kind).isEqualTo(FileKind.PROSE)
        assertThat(without.kind).isNull()
        assertThat(without.hotspot).isNull()
    }

    @Test
    fun `the complexity of files is read through the evidence api with its kind`() {
        val api = object : ContributionEvidenceApi {
            override fun fileContributions(projectId: ProjectId, asOf: Instant): List<FileContribution> = emptyList()

            override fun fileReviews(projectId: ProjectId, asOf: Instant): List<FileReview> = emptyList()

            override fun pageEdits(projectId: ProjectId, asOf: Instant): List<PageEditReading> = emptyList()

            override fun fileComplexities(repositories: Collection<String>) =
                listOf(FileComplexityReading(REPOSITORY, "a.md", FileKind.PROSE, 120.0, 30))

            override fun fileAuthorship(repositories: Collection<String>): List<FileAuthorshipReading> = emptyList()
        }

        assertThat(FileFactsReader(api).of(listOf(REPOSITORY), Identities()).complexities)
            .isEqualTo(mapOf(key("a.md") to Complexity(FileKind.PROSE, 120.0)))
    }

    @Test
    fun `complexity is ranked only among files of the same kind`() {
        val mixed = Hotspots(
            mapOf(key("a.kt") to 1.0, key("b.kt") to 1.0, key("a.md") to 1.0, key("b.md") to 1.0),
            mapOf(
                key("a.kt") to code(1.0),
                key("b.kt") to code(2.0),
                key("a.md") to Complexity(FileKind.PROSE, 500.0),
                key("b.md") to Complexity(FileKind.PROSE, 900.0),
            ),
        )

        assertThat(mixed.of(history("b.kt"))!!).isCloseTo(mixed.of(history("b.md"))!!, within(1e-9))
        assertThat(mixed.of(history("a.md"))!!).isCloseTo(mixed.of(history("a.kt"))!!, within(1e-9))
        assertThat(mixed.of(history("a.md"))!!).isLessThan(mixed.of(history("b.kt"))!!)
    }
}
