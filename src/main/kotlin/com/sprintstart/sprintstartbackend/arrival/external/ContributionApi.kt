package com.sprintstart.sprintstartbackend.arrival.external

import com.sprintstart.sprintstartbackend.arrival.external.model.Contribution
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.user.external.ProjectMember

/** The work a hire produced on a project, reduced to the moments onboarding measures. */
interface ContributionApi {
    /** [member]'s contributions on [projectId], oldest submission first; empty when there is nothing. */
    fun contributionsOf(member: ProjectMember, projectId: ProjectId): List<Contribution>
}
