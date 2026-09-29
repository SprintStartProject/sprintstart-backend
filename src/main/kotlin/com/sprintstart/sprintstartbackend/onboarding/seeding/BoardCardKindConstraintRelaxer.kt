package com.sprintstart.sprintstartbackend.onboarding.seeding

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Drops the `CHECK` constraint Hibernate writes on `board_cards.kind`, on every startup.
 *
 * `board_cards` is created by `ddl-auto: update`, which lists every `BoardCardKind` in a check
 * constraint when it creates the table and never widens it afterwards. A kind added later is then
 * rejected on any database older than the kind — and for a *baseline* kind such as `TASK_POOL`,
 * which is inserted on every board read, that is every board read failing. The enum is the
 * catalog; the column does not need to repeat it.
 *
 * Done here rather than as a migration file because nothing in this service runs migrations:
 * Flyway was removed, and the files under `db/migration` are applied by hand if at all. Running on
 * every start also covers the database created tomorrow, which gets a fresh constraint listing
 * today's kinds and would break again on the next one. Idempotent, and a no-op where the table or
 * the constraint does not exist (H2 in tests names its checks differently and is left alone).
 *
 * A failure is logged rather than thrown: an app that refuses to start is worse than a board that
 * cannot hold a kind it has never held before.
 */
@Component
class BoardCardKindConstraintRelaxer(
    private val jdbcTemplate: JdbcTemplate,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        try {
            jdbcTemplate.execute(DROP_KIND_CHECK)
        } catch (e: DataAccessException) {
            logger.warn("Could not drop board_cards_kind_check; new board card kinds may be rejected: {}", e.message)
        }
    }

    companion object {
        const val DROP_KIND_CHECK =
            "ALTER TABLE IF EXISTS board_cards DROP CONSTRAINT IF EXISTS board_cards_kind_check"
    }
}
