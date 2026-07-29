-- Scopes upload content deduplication to the project that owns the artifact.
--
-- Returning an artifact only by content hash can expose another project's upload when two projects
-- upload identical bytes. The application now looks up hashes together with project_id, and fresh
-- schemas use the same composite uniqueness rule from the JPA entity. Existing rows are backfilled
-- from the ingestion artifact projection when exactly one project is known for the upload artifact.
-- Rows without a deterministic project are left nullable rather than guessed.

ALTER TABLE IF EXISTS uploaded_artifact
    ADD COLUMN IF NOT EXISTS project_id UUID;

WITH upload_artifact_projects AS (
    SELECT
        uploaded_artifact.id AS uploaded_artifact_id,
        MIN(artifact_projects.project_id) AS project_id
    FROM uploaded_artifact
    JOIN artifact
        ON artifact.source_system = 'UPLOAD'
        AND artifact.source_id = uploaded_artifact.id::text
    JOIN artifact_projects
        ON artifact_projects.artifact_id = artifact.id
    GROUP BY uploaded_artifact.id
    HAVING COUNT(DISTINCT artifact_projects.project_id) = 1
)
UPDATE uploaded_artifact
SET project_id = upload_artifact_projects.project_id
FROM upload_artifact_projects
WHERE uploaded_artifact.id = upload_artifact_projects.uploaded_artifact_id
    AND uploaded_artifact.project_id IS NULL;

DO $$
BEGIN
    IF to_regclass('uploaded_artifact') IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM uploaded_artifact WHERE project_id IS NULL) THEN
        ALTER TABLE uploaded_artifact
            ALTER COLUMN project_id SET NOT NULL;
    END IF;
END $$;

ALTER TABLE IF EXISTS uploaded_artifact
    DROP CONSTRAINT IF EXISTS uk_uploaded_artifact_hash;

ALTER TABLE IF EXISTS uploaded_artifact
    DROP CONSTRAINT IF EXISTS uk_uploaded_artifact_uploader_hash;

DO $$
BEGIN
    IF to_regclass('uploaded_artifact') IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_uploaded_artifact_project_hash') THEN
        ALTER TABLE uploaded_artifact
            ADD CONSTRAINT uk_uploaded_artifact_project_hash UNIQUE (project_id, hash);
    END IF;
END $$;
