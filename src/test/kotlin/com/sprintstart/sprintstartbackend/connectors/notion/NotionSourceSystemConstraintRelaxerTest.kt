package com.sprintstart.sprintstartbackend.connectors.notion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID

class NotionSourceSystemConstraintRelaxerTest {
    @Test
    fun `startup relaxer permits Notion on an existing schema`() {
        val dataSource = DriverManagerDataSource(
            "jdbc:h2:mem:notion-relaxer-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
        )
        val jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.execute(legacyTable("artifact"))
        jdbcTemplate.execute(legacyTable("ingestion_run"))

        NotionSourceSystemConstraintRelaxer(jdbcTemplate).run(DefaultApplicationArguments())

        assertThat(jdbcTemplate.update("INSERT INTO artifact (source_system) VALUES ('NOTION')")).isEqualTo(1)
        assertThat(jdbcTemplate.update("INSERT INTO ingestion_run (source_system) VALUES ('NOTION')")).isEqualTo(1)
    }

    private fun legacyTable(tableName: String): String {
        return "CREATE TABLE $tableName (" +
            "source_system VARCHAR(50) NOT NULL, " +
            "CONSTRAINT ${tableName}_source_system_check " +
            "CHECK (source_system IN ('CONFLUENCE', 'GITHUB', 'JIRA', 'UPLOAD')))"
    }
}
