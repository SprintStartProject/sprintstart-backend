package com.sprintstart.sprintstartbackend.starterwork.service

import io.mockk.mockk

/** The real [HireStarterWorkService] over the services a test stubs; the rest answer relaxed. */
fun hireStarterWorkOver(
    currentTaskReader: CurrentTaskReader = mockk(relaxed = true),
    starterWorkTaskProposalService: StarterWorkTaskProposalService = mockk(relaxed = true),
    myCompetencyService: MyCompetencyService = mockk(relaxed = true),
    userGoalService: UserGoalService = mockk(relaxed = true),
    taskOrientationService: TaskOrientationService = mockk(relaxed = true),
    competencyPlacementService: CompetencyPlacementService = mockk(relaxed = true),
) = HireStarterWorkService(
    currentTaskReader,
    starterWorkTaskProposalService,
    myCompetencyService,
    userGoalService,
    taskOrientationService,
    competencyPlacementService,
)

/** The real [HireStarterWorkActionsService] over the services a test stubs; the rest answer relaxed. */
fun hireStarterWorkActionsOver(
    userGoalService: UserGoalService = mockk(relaxed = true),
    competencyPlacementService: CompetencyPlacementService = mockk(relaxed = true),
) = HireStarterWorkActionsService(userGoalService, competencyPlacementService)

/** The real [StarterWorkReviewService] over the services a test stubs; the rest answer relaxed. */
fun starterWorkReviewOver(
    starterWorkTaskProposalService: StarterWorkTaskProposalService = mockk(relaxed = true),
    starterWorkScope: StarterWorkScope = mockk(relaxed = true),
    starterWorkPoolReconciler: StarterWorkPoolReconciler = mockk(relaxed = true),
) = StarterWorkReviewService(starterWorkTaskProposalService, starterWorkScope, starterWorkPoolReconciler)
