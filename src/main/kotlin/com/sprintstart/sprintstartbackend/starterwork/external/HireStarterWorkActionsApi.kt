package com.sprintstart.sprintstartbackend.starterwork.external

import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.StarterWorkTaskId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.starterwork.external.enums.GoalClaimant
import com.sprintstart.sprintstartbackend.starterwork.external.enums.ProficiencyLevel
import com.sprintstart.sprintstartbackend.starterwork.external.model.PlacementResult
import com.sprintstart.sprintstartbackend.starterwork.model.response.goal.GoalView

/** What the buddy changes in a hire's starter work once the hire confirmed it. */
interface HireStarterWorkActionsApi {
    /** Claims [taskId] as the hire's goal on [projectId]; 409 when the task is not live. */
    fun claimGoal(authId: AuthId, projectId: ProjectId, taskId: StarterWorkTaskId, by: GoalClaimant): GoalView

    /** Records where an assessment placed [hireId] on [competencyKey]; never throws for a placement it refuses. */
    fun recordPlacement(hireId: UserId, competencyKey: String, level: ProficiencyLevel): PlacementResult
}
