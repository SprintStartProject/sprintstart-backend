-- AI-generated onboarding content regularly exceeds PostgreSQL's default VARCHAR(255).
-- Keep the persisted aggregate lossless instead of truncating model output or rolling back the
-- entire personalized path. These alterations are safe for existing VARCHAR values and are
-- idempotent when Hibernate's ddl-auto has already aligned the columns with the entity mappings.

ALTER TABLE IF EXISTS onboarding_phases
    ALTER COLUMN title TYPE TEXT,
    ALTER COLUMN description TYPE TEXT;

ALTER TABLE IF EXISTS onboarding_sub_graph_node
    ALTER COLUMN title TYPE TEXT;

ALTER TABLE IF EXISTS onboarding_tasks
    ALTER COLUMN title TYPE TEXT,
    ALTER COLUMN description TYPE TEXT;

ALTER TABLE IF EXISTS onboarding_resources
    ALTER COLUMN title TYPE TEXT,
    ALTER COLUMN description TYPE TEXT,
    ALTER COLUMN url TYPE TEXT;
