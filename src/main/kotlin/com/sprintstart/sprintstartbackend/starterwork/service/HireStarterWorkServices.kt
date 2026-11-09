package com.sprintstart.sprintstartbackend.starterwork.service

import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.StarterWorkTaskId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.starterwork.external.HireStarterWorkActionsApi
import com.sprintstart.sprintstartbackend.starterwork.external.HireStarterWorkApi
import com.sprintstart.sprintstartbackend.starterwork.external.enums.GoalClaimant
import com.sprintstart.sprintstartbackend.starterwork.external.enums.ProficiencyLevel
import com.sprintstart.sprintstartbackend.starterwork.external.model.PlacementResult
import com.sprintstart.sprintstartbackend.starterwork.external.model.PlacementTopic
import com.sprintstart.sprintstartbackend.starterwork.model.response.competency.MyCompetencyResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.goal.GoalView
import com.sprintstart.sprintstartbackend.starterwork.model.response.orientation.MyOrientationResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.RankedStarterWorkTaskResponse
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.StarterWorkTaskProposalResponse
import org.springframework.stereotype.Service
import java.time.Instant

/** [HireStarterWorkApi] over the services that own each read. */
@Service
class HireStarterWorkService(
    private val currentTaskReader: CurrentTaskReader,
    private val starterWorkTaskProposalService: StarterWorkTaskProposalService,
    private val myCompetencyService: MyCompetencyService,
    private val userGoalService: UserGoalService,
    private val taskOrientationService: TaskOrientationService,
    private val competencyPlacementService: CompetencyPlacementService,
) : HireStarterWorkApi {
    override fun currentTaskOf(hireId: UserId, projectId: ProjectId): StarterWorkTaskProposalResponse? =
        currentTaskReader.currentTaskOf(hireId, projectId)

    override fun rankedTasksFor(hireId: UserId, projectId: ProjectId): List<RankedStarterWorkTaskResponse> =
        starterWorkTaskProposalService.matchForUserId(hireId.value, projectId.value)

    override fun competenciesOf(userId: UserId): List<MyCompetencyResponse> =
        myCompetencyService.getCompetenciesForUser(userId.value)

    override fun goalClaimedAt(hireId: UserId, projectId: ProjectId): Instant? =
        userGoalService.claimedAt(hireId, projectId)

    override suspend fun orientationFor(hireId: UserId, projectId: ProjectId): MyOrientationResponse =
        taskOrientationService.getForHire(hireId.value, projectId.value)

    override fun placementTopicsFor(hireId: UserId): List<PlacementTopic> =
        competencyPlacementService.topicsFor(hireId.value)

    override fun competencyLabel(competencyKey: String): String? = competencyPlacementService.labelFor(competencyKey)
}

/** [HireStarterWorkActionsApi] over the services that own each change. */
@Service
class HireStarterWorkActionsService(
    private val userGoalService: UserGoalService,
    private val competencyPlacementService: CompetencyPlacementService,
) : HireStarterWorkActionsApi {
    override fun claimGoal(
        authId: AuthId,
        projectId: ProjectId,
        taskId: StarterWorkTaskId,
        by: GoalClaimant,
    ): GoalView = userGoalService.claimForMe(authId.value, projectId.value, taskId.value, by)

    override fun recordPlacement(hireId: UserId, competencyKey: String, level: ProficiencyLevel): PlacementResult =
        competencyPlacementService.record(hireId.value, competencyKey, level)
}
