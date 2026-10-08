package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.sql.Statement
import java.util.UUID

class BitbucketRepositoryConfigMigrationTest {
    @Test
    fun `creates the config table and backfills existing connections`() {
        val connectionsMigration = loadMigration("V18__add_bitbucket_repository_sync_state.sql")
        val configMigration = loadMigration("V20__add_bitbucket_repository_configs.sql")
        val databaseName = "bitbucket-config-migration-${UUID.randomUUID()}"
        val repositoryId = UUID.randomUUID()

        DriverManager.getConnection("jdbc:h2:mem:$databaseName;MODE=PostgreSQL").use { connection ->
            connection.createStatement().use { statement ->
                execute(statement, connectionsMigration)
                statement.executeUpdate(repositoryInsert(repositoryId))

                // The partial index is a PostgreSQL feature H2 does not parse, so only the statement
                // subset before it is executed; the index DDL itself is asserted on the file below.
                val configStatements = configMigration.substringBefore("CREATE INDEX")
                execute(statement, configStatements)

                statement.executeQuery(configSelect(repositoryId)).use { result ->
                    result.next()
                    assertThat(result.getBoolean("auto_update")).isTrue()
                    assertThat(result.getString("schedule")).isEqualTo("0 0 2 * * *")
                    assertThat(result.getString("spec")).contains("\"type\":\"DAILY\"")
                    assertThat(result.getTimestamp("next_sync_at")).isNotNull()
                }

                // Re-applying the backfill inserts nothing more.
                execute(statement, configStatements)
                statement.executeQuery("SELECT COUNT(*) FROM bb_repository_configs").use { result ->
                    result.next()
                    assertThat(result.getLong(1)).isEqualTo(1)
                }
            }
        }

        assertThat(configMigration).contains(
            "CREATE TABLE IF NOT EXISTS bb_repository_configs",
            "FOREIGN KEY (repository_id) REFERENCES bitbucket_repositories(id) ON DELETE CASCADE",
            "CREATE INDEX IF NOT EXISTS idx_bitbucket_configs_next_sync",
            "WHERE auto_update = TRUE",
        )
    }

    /**
     * Runs the statements of a migration, skipping chunks that are only comments — a trailing
     * comment left behind when the file is cut before its index statement would otherwise be run as
     * SQL and fail.
     */
    private fun execute(statement: Statement, sql: String) {
        sql
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter(::hasNonCommentLine)
            .forEach { statement.execute(it) }
    }

    private fun hasNonCommentLine(block: String): Boolean =
        block.lineSequence().any { line ->
            line.isNotBlank() && !line.trimStart().startsWith("--")
        }

    private fun repositoryInsert(repositoryId: UUID): String =
        "INSERT INTO bitbucket_repositories " +
            "(id, workspace, slug, credential_auth_id, credential_name, connection_state, source_enabled) " +
            "VALUES ('$repositoryId', 'sprintstart', 'backend', 'auth-id', 'team-token', 'UP_TO_DATE', TRUE)"

    private fun configSelect(repositoryId: UUID): String =
        "SELECT auto_update, schedule, spec, next_sync_at FROM bb_repository_configs " +
            "WHERE repository_id = '$repositoryId'"

    private fun loadMigration(fileName: String): String =
        requireNotNull(javaClass.getResourceAsStream("/db/migration/$fileName"))
            .bufferedReader()
            .use { reader -> reader.readText() }
}
