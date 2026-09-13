package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Who put a step on somebody's onboarding path.
 *
 * The badge on a step card used to read this off `isAiAssisted`, which has exactly two values and
 * three answers to give: anything not AI-generated was labelled "Custom step by PM", so a step the
 * hire wrote themselves, and later a step their buddy proposed, both arrived claiming their PM had
 * prescribed it. A hire who cannot tell what their team requires of them from what they agreed to in
 * a chat has lost the distinction the badge exists for.
 *
 * Stored rather than derived, because the endpoint a step was created through is the only thing that
 * knows, and nothing keeps that afterwards.
 */
enum class StepOrigin {
    /** Copied from the blueprint, or assembled for an AI-enhanced phase. The ordinary case. */
    GENERATED,

    /** Written by a PM, HR or an admin on somebody else's path: what the team requires. */
    PM,

    /** Written by the hire on their own path. */
    HIRE,

    /** Proposed by the buddy and confirmed by the hire. Theirs, but not their idea. */
    BUDDY,
}
