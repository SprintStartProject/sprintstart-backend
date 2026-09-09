package com.sprintstart.sprintstartbackend.onboarding.model.request.buddy

/**
 * One thing a hire says to their buddy.
 *
 * [capabilitiesEnabled] is what separates asking the mentor from asking the corpus. With it off the
 * buddy answers from the project's material and does nothing else — no tools, no action proposals,
 * no board writes. Retrieval is unaffected either way: `search_docs` runs AI-side rather than as a
 * backend tool, so "just let me look something up" costs the hire nothing in what they can find.
 *
 * Sent per message rather than held on the session, because it is a mood and not a setting. A hire
 * who turns capabilities off to look something up and then asks the mentor to do something should
 * not have to remember which state they left the switch in, and the transcript stays one
 * conversation either way.
 */
data class SendBuddyMessageRequest(
    val content: String,
    /** Defaults to the full mentor, so a client that has never heard of the switch is unaffected. */
    val capabilitiesEnabled: Boolean = true,
)
