package com.sprintstart.sprintstartbackend.ingestion.model.dto

/**
 * Orderings offered by the project artifact list.
 *
 * Every ordering ends with `id ASC` as a tie-break, so a page boundary never splits or repeats
 * rows that share the leading sort key. Facet counts are order independent and ignore this.
 */
enum class ArtifactSort {
    /** Newest first import: `ingestedAt DESC, id ASC`. The default. */
    ADDED_DESC,

    /** Most recently changed: `coalesce(lastChangedAt, ingestedAt) DESC, id ASC`. */
    CHANGED_DESC,

    /** Alphabetical: `lower(title) ASC NULLS LAST, id ASC`. */
    TITLE_ASC,
}
