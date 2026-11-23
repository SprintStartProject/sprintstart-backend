package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import java.time.Instant

internal const val REPOSITORY = "github:acme/app"

/** The date every scoring test replays unless it says otherwise. */
internal val NOW: Instant = Instant.parse("2026-11-20T12:00:00Z")

/** Noon of the given day, as `yyyy-MM-dd`. */
internal fun day(date: String): Instant = Instant.parse("${date}T12:00:00Z")

/** A day [days] days before [NOW]. */
internal fun daysAgo(days: Long): Instant = NOW.minusSeconds(days * SECONDS_PER_DAY)

private const val SECONDS_PER_DAY = 86_400L

/** One change by [email] made [daysAgo] days before [NOW]. */
internal fun touch(email: String, daysAgo: Long): Touch = Touch(PersonKey.ofEmail(email), daysAgo(daysAgo))

/** A file's history built from changes written as `email to days ago`, in any order. */
internal fun history(path: String, vararg touches: Pair<String, Long>): FileHistory =
    FileHistory(REPOSITORY, path, touches.map { (email, ago) -> touch(email, ago) }.sortedBy { it.at })

/** A file changed by [email] [times] times, starting [firstAgo] days ago and one day apart. */
internal fun repeated(email: String, times: Int, firstAgo: Long): Array<Pair<String, Long>> =
    Array(times) { email to firstAgo - it }

internal fun contribution(
    path: String,
    email: String,
    at: Instant,
    renamedFrom: String? = null,
    repository: String = REPOSITORY,
): FileContribution = FileContribution(repository, path, renamedFrom, email, at)
