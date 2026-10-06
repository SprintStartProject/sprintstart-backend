package com.sprintstart.sprintstartbackend.connectors.notion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

class NotionWorkspaceMigrationTest {
    private val dataSource = DriverManagerDataSource(
        "jdbc:h2:mem:notion-workspace-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "sa",
        "",
    )
    private val jdbc = JdbcTemplate(dataSource)
    private val migration =
        NotionWorkspaceMigration(jdbc, TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private val projectId = UUID.randomUUID()

    @BeforeEach
    fun schema() {
        jdbc.execute("CREATE TABLE notion_credentials (auth_id VARCHAR, name VARCHAR)")
        ResourceDatabasePopulator(ClassPathResource("db/migration/V27__add_notion_workspace_connections.sql"))
            .execute(dataSource)
        jdbc.execute(
            """CREATE TABLE notion_page_connections (
                id UUID PRIMARY KEY, project_id UUID, credential_auth_id VARCHAR, credential_name VARCHAR,
                page_id VARCHAR, page_title VARCHAR, page_url VARCHAR, source_enabled BOOLEAN, auto_update BOOLEAN,
                schedule VARCHAR, schedule_spec VARCHAR, next_sync_at TIMESTAMP WITH TIME ZONE,
                last_synced_at TIMESTAMP WITH TIME ZONE, last_edited_time TIMESTAMP WITH TIME ZONE, content_hash VARCHAR,
                created_at TIMESTAMP WITH TIME ZONE, updated_at TIMESTAMP WITH TIME ZONE, version BIGINT
            )""",
        )
        jdbc.execute(
            "CREATE TABLE artifact (id UUID PRIMARY KEY, source_system VARCHAR, source_id VARCHAR, " +
                "metadata VARCHAR, content_hash VARCHAR)",
        )
        jdbc.execute(
            "CREATE TABLE ingestion_run (id UUID PRIMARY KEY, source_system VARCHAR, " +
                "source_instance_id UUID, source_instance_ref VARCHAR)",
        )
    }

    @Test
    fun `legacy pages merge without losing artifact IDs or run history and restart is safe`() {
        val first = legacyPage("page-1", "2026-01-01T00:00:00Z")
        val second = legacyPage("page-2", "2026-01-02T00:00:00Z")
        val artifactId = UUID.randomUUID()
        val runId = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO artifact VALUES (?, 'NOTION', ?, ?, 'old-hash')",
            artifactId,
            "notion:$second:page:page-2",
            """{"notionConnectionId":"$second"}""",
        )
        jdbc.update("INSERT INTO ingestion_run VALUES (?, 'NOTION', ?, 'page-url')", runId, second)

        migration.run(DefaultApplicationArguments())
        migration.run(DefaultApplicationArguments())

        assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM notion_workspace_connections", Int::class.java),
        ).isEqualTo(1)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notion_synced_pages", Int::class.java)).isEqualTo(2)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notion_page_connections", Int::class.java)).isZero()
        assertThat(
            jdbc.queryForObject("SELECT id FROM notion_workspace_connections", UUID::class.java),
        ).isEqualTo(first)
        assertThat(jdbc.queryForObject("SELECT source_id FROM artifact WHERE id = ?", String::class.java, artifactId))
            .isEqualTo("notion:$first:page:page-2")
        assertThat(
            jdbc.queryForObject(
                "SELECT source_instance_id FROM ingestion_run WHERE id = ?",
                UUID::class.java,
                runId,
            ),
        ).isEqualTo(first)
        assertThat(jdbc.queryForObject("SELECT metadata FROM artifact WHERE id = ?", String::class.java, artifactId))
            .contains(first.toString())
            .doesNotContain(second.toString())
    }

    @Test
    fun `migration failure leaves original rows and no partial workspace copy`() {
        legacyPage("page-1", "2026-01-01T00:00:00Z")
        jdbc.execute("DROP TABLE artifact")

        assertThrows<DataAccessException> { migration.run(DefaultApplicationArguments()) }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notion_page_connections", Int::class.java)).isEqualTo(1)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notion_workspace_connections", Int::class.java)).isZero()
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notion_synced_pages", Int::class.java)).isZero()
    }

    private fun legacyPage(pageId: String, createdAt: String): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """INSERT INTO notion_page_connections
                (id, project_id, credential_auth_id, credential_name, page_id, page_title, page_url,
                 source_enabled, auto_update, schedule, schedule_spec, created_at, updated_at, version)
                VALUES (?, ?, 'user', 'credential', ?, ?, ?, TRUE, FALSE, '0 0 2 * * *', '{}', ?, ?, 0)""",
            id,
            projectId,
            pageId,
            pageId,
            "https://notion.so/$pageId",
            createdAt,
            createdAt,
        )
        return id
    }
}
