package com.sprintstart.sprintstartbackend.connectors.notion

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/** Upgrades legacy page connections after Hibernate creates the workspace tables.
 *
 * The oldest connection per project/credential retains its ID and schedule. Artifact IDs and
 * project memberships remain intact; their source IDs and run histories move to that connection.
 * Legacy rows are removed only in the same transaction as the successful copy, making retries safe.
 */
@Component
internal class NotionWorkspaceMigration(
    private val jdbc: JdbcTemplate,
    private val transaction: TransactionTemplate,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val exists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE LOWER(table_name) = 'notion_page_connections' AND table_schema = CURRENT_SCHEMA",
            Int::class.java,
        ) ?: 0
        if (exists == 0) return
        transaction.executeWithoutResult {
            val rows = jdbc.query(
                "SELECT id, project_id, credential_auth_id, credential_name FROM notion_page_connections " +
                    "ORDER BY created_at, id FOR UPDATE",
            ) { rs, _ ->
                LegacyNotionConnection(
                    UUID.fromString(rs.getString("id")),
                    UUID.fromString(rs.getString("project_id")),
                    rs.getString("credential_auth_id"),
                    rs.getString("credential_name"),
                )
            }
            rows.forEach { migrate(it) }
        }
    }

    private fun migrate(old: LegacyNotionConnection) {
        val existing = jdbc
            .queryForList(
                "SELECT id FROM notion_workspace_connections " +
                    "WHERE project_id = ? AND credential_auth_id = ? AND credential_name = ?",
                UUID::class.java,
                old.projectId,
                old.authId,
                old.credentialName,
            ).firstOrNull()
        val targetId = existing ?: old.id.also { copyConnection(old) }
        jdbc.update(
            "INSERT INTO notion_synced_pages " +
                "(id, connection_id, page_id, page_title, page_url, last_edited_time, content_hash, last_synced_at) " +
                "SELECT id, ?, page_id, page_title, page_url, last_edited_time, NULL, last_synced_at " +
                "FROM notion_page_connections WHERE id = ?",
            targetId,
            old.id,
        )
        jdbc.update(
            "UPDATE artifact SET source_id = REPLACE(source_id, ?, ?), metadata = REPLACE(metadata, ?, ?), " +
                "content_hash = NULL WHERE source_system = 'NOTION' AND source_id LIKE ?",
            "notion:${old.id}:page:",
            "notion:$targetId:page:",
            old.id.toString(),
            targetId.toString(),
            "notion:${old.id}:page:%",
        )
        jdbc.update(
            "UPDATE ingestion_run SET source_instance_id = ?, source_instance_ref = ? " +
                "WHERE source_system = 'NOTION' AND source_instance_id = ?",
            targetId,
            old.credentialName,
            old.id,
        )
        jdbc.update("DELETE FROM notion_page_connections WHERE id = ?", old.id)
    }

    private fun copyConnection(old: LegacyNotionConnection) {
        jdbc.update(
            "INSERT INTO notion_workspace_connections " +
                "(id, project_id, workspace_name, credential_auth_id, credential_name, source_enabled, " +
                "auto_update, schedule, schedule_spec, next_sync_at, last_synced_at, " +
                "created_at, updated_at, version) " +
                "SELECT id, project_id, credential_name, credential_auth_id, credential_name, source_enabled, " +
                "auto_update, schedule, schedule_spec, next_sync_at, last_synced_at, created_at, updated_at, version " +
                "FROM notion_page_connections WHERE id = ?",
            old.id,
        )
    }
}

private data class LegacyNotionConnection(
    val id: UUID,
    val projectId: UUID,
    val authId: String,
    val credentialName: String,
)
