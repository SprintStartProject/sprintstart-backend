package com.sprintstart.sprintstartbackend.onboarding.model.request.competency

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind

/**
 * A PM's edit to one competency node.
 *
 * Every field is optional: an omitted field is left alone, so a client can change a label without
 * restating the rest.
 *
 * [key] is deliberately absent. It is the ledger's identity for this competency -- every
 * `UserCompetencyState` row, every graph edge and every module points at it -- so renaming it
 * would orphan everything somebody has earned. The **label** is what a PM renames.
 */
data class UpdateCompetencyRequest(
    val label: String? = null,
    val description: String? = null,
    /**
     * What this competency is about, for grouping -- "Authentication", "Ingestion".
     *
     * Blank clears it, the way a blank description does: ungrouping a competency is a real edit, and
     * there is no other way to express it when an omitted field means "leave alone". An area that
     * differs from one already in use only by case or spacing is stored as the one already in use.
     */
    val area: String? = null,
    val kind: CompetencyKind? = null,
    /** The proficiency rank (1..4) a hire must reach for this node to count as met. */
    val targetLevel: Int? = null,
)

/**
 * A PM's hand-authored competency node, created without any AI proposal.
 *
 * This is the piece that makes AI genuinely optional for the graph: until it existed, a node's
 * only way in was to approve an AI-generated proposal, so a PM could edit or delete a node by hand
 * but never *originate* one. AI mining stays available as the other way to seed the graph.
 *
 * [key] is the ledger's permanent identity for this competency and is slugified on the way in
 * (lower-cased, non-alphanumerics collapsed to `-`) so hand-typed keys share the house style of
 * generated ones and are safe to carry in a URL. It is the one field creation gets to set and
 * editing never can -- see [UpdateCompetencyRequest].
 */
data class CreateCompetencyRequest(
    val key: String,
    val label: String,
    val description: String? = null,
    /**
     * What this competency is about, for grouping -- "Authentication", "Ingestion". Optional:
     * ungrouped is a real state, and is what everything predating the field reads as.
     */
    val area: String? = null,
    val kind: CompetencyKind,
    /**
     * The proficiency rank (1..4) a hire must reach for this node to count as met. Omitted, it
     * takes the same intermediate default a proposed node gets (`Competency.DEFAULT_TARGET_LEVEL`).
     */
    val targetLevel: Int? = null,
)
