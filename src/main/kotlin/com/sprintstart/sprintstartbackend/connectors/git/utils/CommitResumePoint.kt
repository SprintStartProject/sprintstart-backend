package com.sprintstart.sprintstartbackend.connectors.git.utils

/**
 * The revision a commit ingest should resume from.
 *
 * A connection whose stored commits were read without their changed files has to read the whole
 * history again, because the commits before its cursor can never be revisited otherwise. A blank
 * revision is what [GitIngestionEngine.ingestCommitsSince] reads as "from the beginning", so this
 * returns one while the files are missing and the stored cursor once they are not.
 *
 * Shared by every Git provider so that the rule cannot drift between them.
 *
 * @param lastSyncedSha The revision whose commits were last ingested, blank when none were.
 * @param filesRecorded Whether those commits were read together with the files they changed.
 * @return The revision to resume from, blank to read the whole history.
 */
internal fun commitResumePoint(lastSyncedSha: String, filesRecorded: Boolean): String =
    if (filesRecorded) lastSyncedSha else ""
