-- Backfills project links for GitHub organization metadata artifacts.
INSERT INTO artifact_projects (artifact_id, project_id)
SELECT DISTINCT a.id, p.project_id
FROM artifact a
JOIN gh_repository_connections r ON LOWER(r.owner) = LOWER(a.source_id)
JOIN gh_repository_connection_projects p ON p.repository_connection_id = r.id
WHERE a.artifact_type = 'ORG_METADATA'
  AND NOT EXISTS (
      SELECT 1 FROM artifact_projects ap
      WHERE ap.artifact_id = a.id AND ap.project_id = p.project_id
  );
