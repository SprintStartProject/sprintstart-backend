package com.sprintstart.sprintstartbackend.offboarding.scoring

private val NO_REPLY = Regex("""^(?:\d+\+)?([^@\s]+)@users\.noreply\.github\.com$""")

/**
 * Who a change belongs to, reduced to one comparable value.
 *
 * A GitHub no-reply address carries the account's login, so it is reduced to that login and the
 * same person committing under both forms stays one person. Any other email stands for itself.
 * Matching a commit email to a registered user is a separate concern and not done here.
 */
@JvmInline
value class PersonKey(
    val value: String,
) {
    companion object {
        /** The key of a commit author's email. */
        fun ofEmail(email: String): PersonKey {
            val normalized = email.trim().lowercase()
            return PersonKey(NO_REPLY.matchEntire(normalized)?.groupValues?.get(1) ?: normalized)
        }

        /**
         * The key of a person somebody typed in: an email, or a GitHub login.
         *
         * @throws IllegalArgumentException when [raw] is blank.
         */
        fun parse(raw: String): PersonKey {
            require(raw.isNotBlank()) { "A person must be given as an email or a GitHub login" }
            return if ('@' in raw) ofEmail(raw) else PersonKey(raw.trim().lowercase())
        }
    }
}
