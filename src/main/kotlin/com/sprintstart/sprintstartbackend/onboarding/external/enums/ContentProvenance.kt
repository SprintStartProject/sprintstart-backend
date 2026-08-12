package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Who wrote a piece of module content.
 *
 * Load-bearing for re-synthesis: an AI pass may replace what it wrote before, but must leave
 * [PM]-authored content alone. Without this, regenerating a module would silently discard a
 * human's edits -- the failure that makes an authoring surface untrustworthy.
 */
enum class ContentProvenance { AI, PM }
