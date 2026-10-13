-- The old onboarding -- Task 0 handed out on first read, the ramp, and "autonomy" as the end of
-- onboarding -- is gone: onboarding is the path a PM's blueprint prescribes, and the buddy tutors
-- along it (#311). Idempotent, and safe to run against a database ddl-auto has updated.

-- Nothing reads these tables any more.
DROP TABLE IF EXISTS task_zero_assignments;
DROP TABLE IF EXISTS autonomy_milestones;

-- task_zero_eligible stays, and keeps its meaning: a PM's note that a task is a good first one.
-- What went is the assignment that read it -- the flag is a label on the pool entry now, and
-- nothing hands a flagged task to anybody. The default is for rows written before the column had
-- one.
ALTER TABLE starter_work_task_proposals
    ALTER COLUMN task_zero_eligible SET DEFAULT false;

-- The joined -> first-accepted-work board card. RetiredBoardCardCleanup deletes these on startup
-- as well, because a row of a kind the enum no longer has fails every board read.
DELETE FROM board_cards WHERE kind = 'PATH_TO_FIRST_CONTRIBUTION';
