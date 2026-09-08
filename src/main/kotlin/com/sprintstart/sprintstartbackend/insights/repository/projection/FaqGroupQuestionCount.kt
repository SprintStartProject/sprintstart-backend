package com.sprintstart.sprintstartbackend.insights.repository.projection

import java.util.UUID

/**
 * How many questions one FAQ group received within a time window.
 *
 * Built by a JPQL constructor expression, so the aggregation stays in the database.
 */
data class FaqGroupQuestionCount(
    val groupId: UUID,
    val count: Long,
)
