package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingSubGraphNode
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Steps and knowledge-check questions alike, as the nodes of a phase graph. */
interface OnboardingSubGraphNodeRepository : JpaRepository<OnboardingSubGraphNode, UUID>
