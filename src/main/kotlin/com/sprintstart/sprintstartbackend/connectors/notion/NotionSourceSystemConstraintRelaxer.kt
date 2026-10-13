package com.sprintstart.sprintstartbackend.connectors.notion

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Removes legacy generated source-system checks that do not include `NOTION`.
 *
 * Existing databases may retain Hibernate-generated checks even when manual Flyway scripts are not
 * applied. Running these idempotent statements before ingestion keeps upgrades compatible with the
 * current [com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem] enum.
 */
@Component
internal class NotionSourceSystemConstraintRelaxer(
    private val jdbcTemplate: JdbcTemplate,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        DROP_SOURCE_SYSTEM_CHECKS.forEach { statement ->
            try {
                jdbcTemplate.execute(statement)
            } catch (exception: DataAccessException) {
                logger.warn("Could not relax a source-system constraint: {}", exception.message)
            }
        }
    }

    internal companion object {
        val DROP_SOURCE_SYSTEM_CHECKS = listOf(
            "ALTER TABLE IF EXISTS artifact " +
                "DROP CONSTRAINT IF EXISTS artifact_source_system_check",
            "ALTER TABLE IF EXISTS artifact " +
                "DROP CONSTRAINT IF EXISTS chk_artifact_source_system",
            "ALTER TABLE IF EXISTS ingestion_run " +
                "DROP CONSTRAINT IF EXISTS ingestion_run_source_system_check",
            "ALTER TABLE IF EXISTS ingestion_run " +
                "DROP CONSTRAINT IF EXISTS chk_ingestion_run_source_system",
        )
    }
}
