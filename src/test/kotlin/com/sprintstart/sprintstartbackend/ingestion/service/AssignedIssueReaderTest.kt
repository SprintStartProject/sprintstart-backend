package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraArtifactMetadataWrapper
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraAuthor
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueHistory
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueHistoryItem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueHistorySubitem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueType
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraProject
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * The whole judgement of Jira evidence lives here: a tracked issue arrives as a changelog, not as
 * the flattened columns a pull request has, so every one of the four moments is a decision about
 * what the changelog is allowed to mean.
 */
class AssignedIssueReaderTest {
    private val mapper = ArtifactMetadataJsonMapper(ObjectMapper())
    private val reader = AssignedIssueReader(mapper)

    private val hire = "Ada Lovelace"
    private val colleague = "Grace Hopper"
    private val created = Instant.parse("2026-08-01T09:00:00Z")

    private fun author(name: String) = JiraAuthor(
        displayName = name,
        active = true,
        createdAt = created,
        updatedAt = created,
    )

    private fun change(
        by: String,
        at: Instant,
        field: String,
        from: String = "",
        to: String = "",
    ) = JiraIssueHistoryItem(
        author = author(by),
        createdAt = at,
        items = listOf(JiraIssueHistorySubitem(field = field, fieldtype = "jira", from = from, to = to)),
    )

    private fun issue(
        assignee: String? = hire,
        statusCategory: String = "In Progress",
        history: List<JiraIssueHistoryItem> = emptyList(),
    ): Artifact {
        val metadata = JiraArtifactMetadataWrapper(
            issueType = JiraIssueType(name = "Task", description = ""),
            issueKey = "ONB-42",
            statusName = statusCategory,
            statusDescription = "",
            statusCategory = statusCategory,
            createdBy = author(colleague),
            reportedBy = author(colleague),
            assignee = assignee?.let { author(it) },
            project = JiraProject(key = "ONB", name = "Onboarding", projectTypeKey = "software"),
            history = JiraIssueHistory(historyItems = history),
            comments = emptyList(),
        )
        return Artifact(
            sourceSystem = SourceSystem.JIRA,
            sourceId = "jira:ONB-42",
            sourceUrl = "https://example.test/browse/ONB-42",
            artifactType = ArtifactType.ISSUE,
            title = "Run the sprint retro",
            content = null,
            mime = null,
            language = null,
            metadata = mapper.toJson(metadata),
            createdAtSource = created,
            updatedAtSource = created,
            ingestionRun = mockk<IngestionRun>(relaxed = true),
            hash = null,
        )
    }

    @Test
    fun `an issue assigned to somebody else is not theirs`() {
        assertThat(reader.read(issue(assignee = colleague), hire)).isNull()
    }

    @Test
    fun `an unassigned issue belongs to nobody`() {
        assertThat(reader.read(issue(assignee = null), hire)).isNull()
    }

    /**
     * The four moments, in the shape the ramp reads them: handed over, somebody looked, somebody
     * else accepted it.
     */
    @Test
    fun `reads the moments from the changelog`() {
        val assigned = created.plusSeconds(3_600)
        val touched = created.plusSeconds(7_200)
        val done = created.plusSeconds(10_800)

        val result = reader.read(
            issue(
                statusCategory = "Done",
                history = listOf(
                    change(by = colleague, at = assigned, field = "assignee", to = hire),
                    change(by = colleague, at = touched, field = "status", from = "To Do", to = "In Review"),
                    change(by = colleague, at = done, field = "status", from = "In Review", to = "Done"),
                ),
            ),
            hire,
        )

        assertThat(result).isNotNull
        assertThat(result!!.openedAt).isEqualTo(assigned)
        assertThat(result.firstResponseAt).isEqualTo(assigned)
        assertThat(result.acceptedAt).isEqualTo(done)
        assertThat(result.key).isEqualTo("ONB-42")
    }

    /**
     * ⚠️ The rule this source exists for. Closing your own ticket is a *claim*; the whole point of
     * observing a tracker is evidence nobody had to vouch for — the same reason a hire may not
     * attest their own work. Such an issue stays in flight rather than being recorded as a weaker
     * acceptance, because a downgraded acceptance is still counted somewhere.
     */
    @Test
    fun `an issue the hire moved to done themselves is not accepted`() {
        val result = reader.read(
            issue(
                statusCategory = "Done",
                history = listOf(
                    change(by = hire, at = created.plusSeconds(60), field = "status", from = "To Do", to = "Done"),
                ),
            ),
            hire,
        )

        assertThat(result).isNotNull
        assertThat(result!!.acceptedAt).isNull()
    }

    /**
     * An issue that was already finished before this project connected its tracker was not accepted
     * *here*, and dating it from the ingest would invent a moment nobody observed.
     */
    @Test
    fun `a done issue with no changelog behind it has no acceptance moment`() {
        val result = reader.read(issue(statusCategory = "Done", history = emptyList()), hire)

        assertThat(result).isNotNull
        assertThat(result!!.acceptedAt).isNull()
    }

    /**
     * ⚠️ Rework is half the operational definition of autonomy — "done, with nothing sent back" —
     * so a flat zero here would hand every tracked issue a clean run it had not earned. Somebody
     * else moving the issue out of a status the hire put it in is the tracker's version of a review
     * asking for changes.
     */
    @Test
    fun `counts the times somebody else moved it back out of where the hire put it`() {
        val result = reader.read(
            issue(
                statusCategory = "Done",
                history = listOf(
                    change(by = hire, at = created.plusSeconds(60), field = "status", to = "In Review"),
                    change(by = colleague, at = created.plusSeconds(120), field = "status", to = "In Progress"),
                    change(by = hire, at = created.plusSeconds(180), field = "status", to = "In Review"),
                    change(by = colleague, at = created.plusSeconds(240), field = "status", to = "In Progress"),
                    change(by = hire, at = created.plusSeconds(300), field = "status", to = "In Review"),
                    change(by = colleague, at = created.plusSeconds(360), field = "status", to = "Done"),
                ),
            ),
            hire,
        )

        // Three hand-offs to somebody else, of which the last one accepted it: two were send-backs.
        assertThat(result!!.returnedCount).isEqualTo(2)
    }

    /** The hire's own moves through their own board are the normal flow of work, not rework. */
    @Test
    fun `the hire moving their own issue along is not rework`() {
        val result = reader.read(
            issue(
                history = listOf(
                    change(by = hire, at = created.plusSeconds(60), field = "status", to = "In Progress"),
                    change(by = hire, at = created.plusSeconds(120), field = "status", to = "In Review"),
                ),
            ),
            hire,
        )

        assertThat(result!!.returnedCount).isEqualTo(0)
    }

    /**
     * A hire who was handed a piece of work, lost it and got it back has been waiting since the
     * first hand-over. Taking the latest assignment would reset their clock every time somebody
     * reassigned around them.
     */
    @Test
    fun `the clock starts at the first time it became theirs`() {
        val first = created.plusSeconds(60)
        val again = created.plusSeconds(6_000)

        val result = reader.read(
            issue(
                history = listOf(
                    change(by = colleague, at = first, field = "assignee", to = hire),
                    change(by = colleague, at = created.plusSeconds(3_000), field = "assignee", to = colleague),
                    change(by = colleague, at = again, field = "assignee", to = hire),
                ),
            ),
            hire,
        )

        assertThat(result!!.openedAt).isEqualTo(first)
    }

    /** Assigned at creation and never re-assigned: the issue's own creation is when it became theirs. */
    @Test
    fun `falls back to when the issue was created`() {
        val result = reader.read(issue(history = emptyList()), hire)

        assertThat(result!!.openedAt).isEqualTo(created)
    }

    /**
     * Only somebody *else* counts as a response. A hire working alone on their own issue has not
     * been answered, however much they touch it — reporting otherwise would tell a PM the team
     * replied when nobody has.
     */
    @Test
    fun `the hire's own activity is not a response`() {
        val result = reader.read(
            issue(
                history = listOf(
                    change(by = hire, at = created.plusSeconds(60), field = "status", to = "In Progress"),
                    change(by = hire, at = created.plusSeconds(120), field = "description"),
                ),
            ),
            hire,
        )

        assertThat(result!!.firstResponseAt).isNull()
    }

    /** Jira renders one name; a person types it back with the case they remember. */
    @Test
    fun `matches the assignee without caring about case`() {
        assertThat(reader.read(issue(), "ada lovelace")).isNotNull
    }
}
