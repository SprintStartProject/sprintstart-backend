package com.sprintstart.sprintstartbackend.ingestion.repository

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement
import java.util.UUID

/**
 * Runs the migration that admits BITBUCKET as a source system against the constraints it replaces.
 *
 * The check constraints are what the database enforces independently of the SourceSystem enum, so
 * the behaviour that matters is which values an insert is allowed to carry before and after.
 */
class BitbucketSourceSystemMigrationTest {
    @Test
    fun `refuses a Bitbucket artifact and run before the migration`() {
        withOldConstraints { statement ->
            assertThatThrownBy { statement.executeUpdate(insertArtifact("BITBUCKET")) }
                .isInstanceOf(SQLException::class.java)
            assertThatThrownBy { statement.executeUpdate(insertRun("BITBUCKET")) }
                .isInstanceOf(SQLException::class.java)
        }
    }

    @Test
    fun `accepts a Bitbucket artifact and run after the migration`() {
        withOldConstraints { statement ->
            execute(statement, loadMigration())

            statement.executeUpdate(insertArtifact("BITBUCKET"))
            statement.executeUpdate(insertRun("BITBUCKET"))

            statement.executeQuery("SELECT COUNT(*) FROM artifact WHERE source_system = 'BITBUCKET'").use {
                it.next()
                assertThat(it.getLong(1)).isEqualTo(1)
            }
        }
    }

    @Test
    fun `keeps accepting the systems that were allowed before`() {
        withOldConstraints { statement ->
            execute(statement, loadMigration())

            listOf("CONFLUENCE", "GITHUB", "JIRA", "UPLOAD").forEach { system ->
                statement.executeUpdate(insertArtifact(system))
                statement.executeUpdate(insertRun(system))
            }
        }
    }

    @Test
    fun `still refuses an unknown source system`() {
        withOldConstraints { statement ->
            execute(statement, loadMigration())

            assertThatThrownBy { statement.executeUpdate(insertArtifact("GITLAB")) }
                .isInstanceOf(SQLException::class.java)
            assertThatThrownBy { statement.executeUpdate(insertRun("GITLAB")) }
                .isInstanceOf(SQLException::class.java)
        }
    }

    @Test
    fun `can be applied twice`() {
        withOldConstraints { statement ->
            val migration = loadMigration()

            execute(statement, migration)
            execute(statement, migration)

            statement.executeUpdate(insertArtifact("BITBUCKET"))
        }
    }

    /**
     * Opens a fresh in-memory database holding the two tables with the constraints as V11 left them,
     * the state a production database is in when this migration runs.
     */
    private fun withOldConstraints(block: (Statement) -> Unit) {
        val databaseName = "bitbucket-source-system-migration-${UUID.randomUUID()}"

        DriverManager.getConnection("jdbc:h2:mem:$databaseName;MODE=PostgreSQL").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE ingestion_run (id UUID PRIMARY KEY, source_system VARCHAR(50) NOT NULL, " +
                        "CONSTRAINT chk_ingestion_run_source_system " +
                        "CHECK (source_system IN ('CONFLUENCE', 'GITHUB', 'JIRA', 'UPLOAD')))",
                )
                statement.execute(
                    "CREATE TABLE artifact (id UUID PRIMARY KEY, source_system VARCHAR(50) NOT NULL, " +
                        "CONSTRAINT chk_artifact_source_system " +
                        "CHECK (source_system IN ('CONFLUENCE', 'GITHUB', 'JIRA', 'UPLOAD')))",
                )
                block(statement)
            }
        }
    }

    private fun insertArtifact(sourceSystem: String): String =
        "INSERT INTO artifact (id, source_system) VALUES ('${UUID.randomUUID()}', '$sourceSystem')"

    private fun insertRun(sourceSystem: String): String =
        "INSERT INTO ingestion_run (id, source_system) VALUES ('${UUID.randomUUID()}', '$sourceSystem')"

    /** Runs the statements of a migration, skipping chunks that hold nothing but comments. */
    private fun execute(statement: Statement, sql: String) {
        sql
            .split(';')
            .map { it.trim() }
            .filter { block -> block.lineSequence().any { it.isNotBlank() && !it.trimStart().startsWith("--") } }
            .forEach { statement.execute(it) }
    }

    private fun loadMigration(): String =
        requireNotNull(javaClass.getResourceAsStream("/db/migration/V25__allow_bitbucket_source_system.sql"))
            .bufferedReader()
            .use { reader -> reader.readText() }
}
