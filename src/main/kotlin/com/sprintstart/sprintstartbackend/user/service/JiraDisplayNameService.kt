package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * Owns writing a user's Jira display name: normalization and uniqueness.
 *
 * The sibling of [GithubLoginService], and it exists for the same reason rather than for symmetry:
 * this value is what attributes an ingested Jira issue to a hire, so getting it wrong is a
 * measurement bug, not a cosmetic profile flaw. One writer, however many entry points.
 *
 * ⚠️ **Not lower-cased**, unlike a GitHub login: this is a person's name, matched against what
 * Jira renders, so folding case would both misspell somebody and stop matching.
 *
 * ⚠️ **No syntax rule.** Anything Jira renders is a valid display name; a pattern would reject real
 * people for looking wrong to a regex.
 *
 * ⚠️ **It cannot defend against a namesake inside Jira.** Uniqueness here means no two *SprintStart
 * users* claim one name. Two Jira accounts genuinely sharing a display name are indistinguishable —
 * the connector drops Jira's `accountId` at parse time — and their work lands on one record.
 * Parsing that id is the fix when somebody hits it.
 */
@Service
class JiraDisplayNameService(
    private val userRepository: UserRepository,
) {
    /**
     * Stores [jiraDisplayName] on [user].
     *
     * A blank value clears it, so somebody can withdraw a wrong name rather than being stuck with
     * it — and clearing is also how a hire opts out of having their Jira work counted at all.
     *
     * @throws ResponseStatusException 409 when another user already claims it, which would credit
     * one person's issues to the other.
     */
    fun apply(user: User, jiraDisplayName: String) {
        // Collapsed rather than merely trimmed: a name pasted out of Jira's UI arrives with the
        // odd double space, and "Ada  Lovelace" must not be a different person from "Ada Lovelace".
        val normalized = jiraDisplayName.trim().replace(WHITESPACE_RUN, " ")

        if (normalized.isEmpty()) {
            user.jiraDisplayName = null
            return
        }

        if (userRepository.existsByJiraDisplayNameAndIdNot(normalized, user.id)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Jira user '$normalized' is already linked to another user",
            )
        }

        user.jiraDisplayName = normalized
    }

    private companion object {
        val WHITESPACE_RUN = Regex("\\s+")
    }
}
