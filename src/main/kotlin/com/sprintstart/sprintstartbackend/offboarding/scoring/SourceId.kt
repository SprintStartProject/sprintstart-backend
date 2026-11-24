package com.sprintstart.sprintstartbackend.offboarding.scoring

/**
 * Names a kind of evidence, such as the commits a file received.
 *
 * It keys the weights of a [WeightProfile] and the per-source breakdown of a [FileRisk].
 */
@JvmInline
value class SourceId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "A source needs a name" }
    }

    companion object {
        /** The changes commits made to files. */
        val COMMITS = SourceId("commits")

        /** The pull requests a person reviewed, per file. */
        val REVIEWS = SourceId("reviews")
    }
}
