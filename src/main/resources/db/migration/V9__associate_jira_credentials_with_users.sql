ALTER TABLE jira_credentials
    ADD COLUMN IF NOT EXISTS auth_id VARCHAR(255);

DO $$
BEGIN
    IF to_regclass('public.sprintstart_users') IS NOT NULL THEN
        UPDATE jira_credentials c
        SET auth_id = u.auth_id
        FROM sprintstart_users u
        WHERE LOWER(u.email) = LOWER(c.user_email);
    END IF;
END $$;

UPDATE jira_credentials
SET auth_id = user_email
WHERE auth_id IS NULL;

ALTER TABLE jira_credentials
    ALTER COLUMN auth_id SET NOT NULL,
    DROP CONSTRAINT IF EXISTS jira_credentials_pkey,
    ADD PRIMARY KEY (auth_id, name);

ALTER TABLE jira_instances
    ADD COLUMN IF NOT EXISTS update_credential_auth_id VARCHAR(255);

UPDATE jira_instances i
SET update_credential_auth_id = c.auth_id
FROM jira_credentials c
WHERE LOWER(c.user_email) = LOWER(i.update_credential_user_email)
  AND c.name = i.update_credential_name;

UPDATE jira_instances
SET update_credential_auth_id = update_credential_user_email
WHERE update_credential_auth_id IS NULL;

ALTER TABLE jira_instances
    ALTER COLUMN update_credential_auth_id SET NOT NULL;
