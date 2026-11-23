package com.sprintstart.sprintstartbackend.shared.git

import java.time.Instant

/**
 * One commit as read from a local clone.
 *
 * This is deliberately the narrow subset every hosted Git provider can supply from the clone
 * itself: identity, authorship, subject and the files changed. Provider-specific commit data, such
 * as the API's verification state or its own user objects, has to come from that provider's client
 * instead.
 *
 * [authorName] is a Git author *name*, not an account handle. They are not interchangeable: a name
 * is free text chosen by the committer, so it must not be used to attribute a commit to an account.
 * [authorEmail] is just as free text, but it is the only authorship signal that can be matched to an
 * account afterwards, for example through a provider's no-reply address.
 *
 * @property sha The full commit SHA.
 * @property authorName The Git author name, as recorded in the commit.
 * @property authorEmail The Git author email, as recorded in the commit.
 * @property committedAt The commit time, normalised to an instant.
 * @property subject The first line of the commit message.
 * @property files The files the commit changed.
 */
data class GitCommit(
    val sha: String,
    val authorName: String,
    val authorEmail: String,
    val committedAt: Instant,
    val subject: String,
    val files: CommitFiles,
)
