package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class BitbucketRepositoryProjectsMigrationTest {
    @Test
    fun `adds the pull request cursor and the project link table`() {
        val connectionsMigration = loadMigration("V18__add_bitbucket_repository_sync_state.sql")
        val cursorMigration = loadMigration("V21__add_bitbucket_pull_request_sync_state.sql")
        val projectsMigration = loadMigration("V22__add_bitbucket_repository_projects.sql")
        val databaseName = "bitbucket-projects-migration-${UUID.randomUUID()}"
        val repositoryId = UUID.randomUUID()
        val projectId = UUID.randomUUID()

        DriverManager.getConnection("jdbc:h2:mem:$databaseName;MODE=PostgreSQL").use { connection ->
            connection.createStatement().use { statement ->
                execute(statement, connectionsMigration)
                statement.executeUpdate(repositoryInsert(repositoryId))

                // A connection that predates the cursor starts with no pull-request sync recorded,
                // which is what makes ingestion read the whole history on its first run.
                execute(statement, cursorMigration)
                assertThat(readCursor(connection, repositoryId)).isNull()

                // The column holds an instant, rounding back to the same value it was given.
                val cursor = Instant.parse("2026-07-06T12:00:00Z")
                connection
                    .prepareStatement(
                        "UPDATE bitbucket_repositories SET last_pr_sync = ? WHERE id = ?",
                    ).use { update ->
                        update.setTimestamp(1, Timestamp.from(cursor))
                        update.setObject(2, repositoryId)
                        update.executeUpdate()
                    }
                assertThat(readCursor(connection, repositoryId)).isEqualTo(cursor)

                // Re-applying the idempotent column addition neither fails nor resets the cursor.
                execute(statement, cursorMigration)
                assertThat(readCursor(connection, repositoryId)).isEqualTo(cursor)

                execute(statement, projectsMigration)
                statement.executeUpdate(projectLinkInsert(repositoryId, projectId))
                assertThat(countLinks(statement)).isEqualTo(1)

                // Re-applying the idempotent create keeps the existing link.
                execute(statement, projectsMigration)
                assertThat(countLinks(statement)).isEqualTo(1)

                // A row cannot be linked twice; the link is identified by the pair.
                assertThrows<SQLException> {
                    statement.executeUpdate(projectLinkInsert(repositoryId, projectId))
                }
                assertThat(countLinks(statement)).isEqualTo(1)

                // Deleting the connection takes its links with it, so no link outlives its repository.
                statement.executeUpdate("DELETE FROM bitbucket_repositories WHERE id = '$repositoryId'")
                assertThat(countLinks(statement)).isZero()
            }
        }

        assertThat(cursorMigration).contains("ADD COLUMN IF NOT EXISTS last_pr_sync TIMESTAMP WITH TIME ZONE")
        assertThat(projectsMigration).contains(
            "CREATE TABLE IF NOT EXISTS bitbucket_repository_projects",
            "PRIMARY KEY (repository_id, project_id)",
            "FOREIGN KEY (repository_id) REFERENCES bitbucket_repositories(id) ON DELETE CASCADE",
            "CREATE INDEX IF NOT EXISTS idx_bitbucket_repository_projects_project",
        )
    }

    /**
     * Runs the statements of a migration, skipping chunks that are only comments — a trailing
     * comment left behind when the file is cut before a statement would otherwise be run as SQL and
     * fail.
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

    private fun readCursor(connection: java.sql.Connection, repositoryId: UUID): Instant? =
        connection.prepareStatement("SELECT last_pr_sync FROM bitbucket_repositories WHERE id = ?").use { select ->
            select.setObject(1, repositoryId)
            select.executeQuery().use { result ->
                result.next()
                result.getTimestamp("last_pr_sync")?.toInstant()
            }
        }

    private fun countLinks(statement: Statement): Long =
        statement.executeQuery("SELECT COUNT(*) FROM bitbucket_repository_projects").use { result ->
            result.next()
            result.getLong(1)
        }

    private fun repositoryInsert(repositoryId: UUID): String =
        "INSERT INTO bitbucket_repositories " +
            "(id, workspace, slug, credential_auth_id, credential_name, connection_state, source_enabled) " +
            "VALUES ('$repositoryId', 'sprintstart', 'backend', 'auth-id', 'team-token', 'UP_TO_DATE', TRUE)"

    private fun projectLinkInsert(repositoryId: UUID, projectId: UUID): String =
        "INSERT INTO bitbucket_repository_projects (repository_id, project_id) " +
            "VALUES ('$repositoryId', '$projectId')"

    private fun loadMigration(fileName: String): String =
        requireNotNull(javaClass.getResourceAsStream("/db/migration/$fileName"))
            .bufferedReader()
            .use { reader -> reader.readText() }
}
