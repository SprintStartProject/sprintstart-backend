package com.sprintstart.sprintstartbackend.connectors.notion.repository

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

class NotionSourceSystemMigrationTest {
    @Test
    fun `migration replaces legacy source system constraints and permits Notion`() {
        val databaseName = "notion-source-system-migration-${UUID.randomUUID()}"
        DriverManager.getConnection("jdbc:h2:mem:$databaseName;MODE=PostgreSQL").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(legacyTable("ingestion_run"))
                statement.execute(legacyTable("artifact"))

                loadMigration()
                    .split(';')
                    .map { sql -> sql.trim() }
                    .filter { sql -> sql.isNotEmpty() }
                    .forEach { sql -> statement.execute(sql) }

                statement.executeUpdate("INSERT INTO ingestion_run (source_system) VALUES ('NOTION')")
                statement.executeUpdate("INSERT INTO artifact (source_system) VALUES ('NOTION')")

                assertThatThrownBy {
                    statement.executeUpdate("INSERT INTO ingestion_run (source_system) VALUES ('UNKNOWN')")
                }.isInstanceOf(SQLException::class.java)
            }
        }
    }

    private fun legacyTable(tableName: String): String {
        return "CREATE TABLE $tableName (" +
            "source_system VARCHAR(50) NOT NULL, " +
            "CONSTRAINT ${tableName}_source_system_check " +
            "CHECK (source_system IN ('CONFLUENCE', 'GITHUB', 'JIRA', 'UPLOAD')), " +
            "CONSTRAINT chk_${tableName}_source_system " +
            "CHECK (source_system IN ('CONFLUENCE', 'GITHUB', 'JIRA', 'UPLOAD')))"
    }

    private fun loadMigration(): String {
        return requireNotNull(
            javaClass.getResourceAsStream(
                "/db/migration/V19__allow_notion_source_system_for_existing_schemas.sql",
            ),
        ).bufferedReader().use { reader -> reader.readText() }
    }
}
