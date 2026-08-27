package com.sprintstart.sprintstartbackend.connectors.confluence.repository

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

class ConfluenceConnectionMigrationTest {
    @Test
    fun `migration applies with ownership uniqueness and cascading collections`() {
        val migration = loadMigration()
        val sourceEnabledMigration = loadMigration("V13__add_confluence_source_enabled.sql")
        val sourceIdentityMigration = loadMigration("V14__add_confluence_artifact_source_identity.sql")
        val databaseName = "confluence-migration-${UUID.randomUUID()}"
        DriverManager.getConnection("jdbc:h2:mem:$databaseName;MODE=PostgreSQL").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE sprintstart_projects (id UUID PRIMARY KEY)")
                migration
                    .split(';')
                    .map { sql -> sql.trim() }
                    .filter { sql -> sql.isNotEmpty() }
                    .forEach { sql -> statement.execute(sql) }
                sourceEnabledMigration
                    .split(';')
                    .map { sql -> sql.trim() }
                    .filter { sql -> sql.isNotEmpty() }
                    .forEach { sql -> statement.execute(sql) }

                val projectId = UUID.randomUUID()
                val connectionId = UUID.randomUUID()
                statement.executeUpdate("INSERT INTO sprintstart_projects (id) VALUES ('$projectId')")
                statement.executeUpdate(connectionInsert(connectionId, projectId))
                statement.executeUpdate(
                    "INSERT INTO confluence_credentials " +
                        "(id, connection_id, user_email, api_token, created_at) VALUES " +
                        "('${UUID.randomUUID()}', '$connectionId', 'fake-user@example.invalid', " +
                        "'encrypted-test-value', CURRENT_TIMESTAMP)",
                )

                assertThatThrownBy {
                    statement.executeUpdate(connectionInsert(UUID.randomUUID(), projectId))
                }.isInstanceOf(SQLException::class.java)

                statement.executeUpdate("DELETE FROM confluence_space_connections WHERE id = '$connectionId'")
                val credentialCountQuery =
                    "SELECT COUNT(*) FROM confluence_credentials WHERE connection_id = '$connectionId'"
                statement.executeQuery(credentialCountQuery).use { result ->
                    result.next()
                    assertThat(result.getLong(1)).isZero()
                }
            }
        }

        assertThat(migration).contains(
            "FOREIGN KEY (project_id) REFERENCES sprintstart_projects(id) ON DELETE CASCADE",
            "UNIQUE (project_id, base_url, space_id)",
            "api_token TEXT NOT NULL",
            "FOREIGN KEY (connection_id) REFERENCES confluence_space_connections(id) ON DELETE CASCADE",
            "CREATE INDEX idx_confluence_connection_project",
        )
        assertThat(sourceEnabledMigration).contains("source_enabled BOOLEAN NOT NULL DEFAULT TRUE")
        assertThat(sourceIdentityMigration).contains(
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_artifact_confluence_source_identity",
            "WHERE source_system = 'CONFLUENCE'",
        )
        assertThat(migration.lowercase()).doesNotContain("default '")
    }

    private fun loadMigration(fileName: String = "V12__add_confluence_connections.sql"): String {
        return requireNotNull(
            javaClass.getResourceAsStream("/db/migration/$fileName"),
        ).bufferedReader().use { reader -> reader.readText() }
    }

    private fun connectionInsert(connectionId: UUID, projectId: UUID): String {
        return "INSERT INTO confluence_space_connections " +
            "(id, project_id, base_url, space_id, space_key, source_enabled, created_at, updated_at, version) VALUES " +
            "('$connectionId', '$projectId', 'https://tenant.atlassian.net', '123', 'ENG', TRUE, " +
            "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)"
    }
}
