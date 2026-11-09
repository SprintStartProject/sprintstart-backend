package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.path.external.PathReadApi
import com.sprintstart.sprintstartbackend.path.external.model.OnboardingPosition
import com.sprintstart.sprintstartbackend.path.external.model.ResolvedPathStep
import com.sprintstart.sprintstartbackend.shared.ids.OnboardingTaskId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import org.springframework.stereotype.Service
import java.util.UUID

/** [PathReadApi] over the readers and services that own each answer. */
@Service
class PathReadService(
    private val pathStepReader: PathStepReader,
    private val onboardingTaskService: OnboardingTaskService,
    private val onboardingPositionReader: OnboardingPositionReader,
) : PathReadApi {
    override fun stepsById(userId: UserId): Map<UUID, ResolvedPathStep> = pathStepReader.stepsById(userId.value)

    override fun resolveStep(userId: UserId, reference: String): ResolvedPathStep? =
        pathStepReader.resolve(userId.value, reference)

    override fun setTaskFinished(userId: UserId, taskId: OnboardingTaskId, finished: Boolean) =
        onboardingTaskService.setFinishedForUser(userId.value, taskId.value, finished)

    override fun positionsOf(userIds: Collection<UserId>): Map<UserId, OnboardingPosition> =
        onboardingPositionReader
            .positionsFor(userIds.map { it.value })
            .mapKeys { (id, _) -> UserId(id) }
}
