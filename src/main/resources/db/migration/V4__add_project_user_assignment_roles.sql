DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'pk_user_projects'
    ) THEN
        ALTER TABLE user_projects
            ADD CONSTRAINT pk_user_projects PRIMARY KEY (user_id, project_id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS user_project_assignment_roles (
    user_id UUID NOT NULL,
    project_id UUID NOT NULL,
    role_id UUID NOT NULL,

    CONSTRAINT pk_user_project_assignment_roles
        PRIMARY KEY (user_id, project_id, role_id),

    CONSTRAINT fk_upar_user_project
        FOREIGN KEY (user_id, project_id)
            REFERENCES user_projects(user_id, project_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_upar_role_id
        FOREIGN KEY (role_id)
            REFERENCES sprintstart_project_roles(id)
            ON DELETE CASCADE
);
