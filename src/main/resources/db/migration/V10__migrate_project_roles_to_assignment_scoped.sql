-- Migrates project roles from the person-wide model to the per-project (assignment-scoped) model.
--
-- Before: a role was a fact about the person, stored once in `user_project_roles`, and applied to
--         every project they belonged to. The application no longer reads that table.
-- After:  a role is a fact about a membership, stored in `user_project_assignment_roles` (the V4
--         table), scoped to one (user_id, project_id) pair. Only this table is read now.
--
-- What this migration does: every person-wide role is copied onto each of that user's project
-- memberships, preserving the old "applies everywhere" behaviour under the new model. Nothing is
-- deleted -- `user_project_roles` is left in place so the change is reversible by hand if needed.
--
-- Two caveats that cannot be migrated honestly:
--   * A user holding roles but belonging to NO project has no membership to scope the role to.
--     Those rows cannot be carried over and are surfaced with a NOTICE instead of being dropped
--     silently.
--   * Where a user belongs to several projects, the old model stored no per-project distinction,
--     so the role is copied to all of them. That is the faithful reading of "person-wide" and the
--     reason this feature was built, but it is worth knowing the copy cannot recover intent the
--     old schema never recorded.
--
-- Idempotent: guarded on the legacy table existing, and ON CONFLICT DO NOTHING on the target
-- primary key, so re-running it (or running it against a schema Hibernate already rebuilt from
-- entities, where `user_project_roles` may not exist) is a no-op.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'user_project_roles'
    ) THEN
        -- Copy each person-wide role onto every membership the user holds.
        INSERT INTO user_project_assignment_roles (user_id, project_id, role_id)
        SELECT upr.user_id, up.project_id, upr.role_id
        FROM user_project_roles upr
        JOIN user_projects up ON up.user_id = upr.user_id
        ON CONFLICT ON CONSTRAINT pk_user_project_assignment_roles DO NOTHING;

        -- Surface roles that could not be carried over because the user is on no project.
        IF EXISTS (
            SELECT 1
            FROM user_project_roles upr
            WHERE NOT EXISTS (
                SELECT 1 FROM user_projects up WHERE up.user_id = upr.user_id
            )
        ) THEN
            RAISE NOTICE 'user_project_roles holds role(s) for user(s) with no project membership; '
                'these were not migrated to user_project_assignment_roles and are now unread by the '
                'application. Review user_project_roles manually if that data matters.';
        END IF;
    END IF;
END $$;
