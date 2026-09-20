ALTER TABLE sprintstart_skills ADD COLUMN category VARCHAR(255);
ALTER TABLE sprintstart_skills ADD COLUMN universal BOOLEAN NOT NULL DEFAULT FALSE;