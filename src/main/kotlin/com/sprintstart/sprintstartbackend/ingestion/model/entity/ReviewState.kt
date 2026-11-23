package com.sprintstart.sprintstartbackend.ingestion.model.entity

/**
 * How a reviewer ended a review they submitted.
 *
 * Only submitted reviews are recorded, so a review that is still a draft has no state here.
 */
enum class ReviewState {
    /** The reviewer approved the change. */
    APPROVED,

    /** The reviewer asked for changes before they would approve. */
    CHANGES_REQUESTED,

    /** The reviewer left comments without a verdict. */
    COMMENTED,

    /** The review was dismissed after it was submitted; the reviewer still looked at the change. */
    DISMISSED,
}
