-- Confluence connections now reference a shared Atlassian credential (its owner and its name) instead of
-- storing their own encrypted token. An existing connection carries no reference to such a credential and
-- cannot be mapped onto one, so connections are removed before the reference becomes mandatory. Their
-- credentials, page allowlists and denylists are removed with them by the existing cascades.
DELETE FROM confluence_space_connections;

ALTER TABLE confluence_space_connections
    ADD COLUMN credential_auth_id VARCHAR(255) NOT NULL;

ALTER TABLE confluence_space_connections
    ADD COLUMN credential_name VARCHAR(255) NOT NULL;

DROP TABLE confluence_credentials;
