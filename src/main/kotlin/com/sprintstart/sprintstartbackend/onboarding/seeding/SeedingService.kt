package com.sprintstart.sprintstartbackend.onboarding.seeding

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingResource
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTask
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SeedingService(
    private val onboardingPathRepository: OnboardingPathRepository,
    private val userApi: UserApi,
    private val resourceLoader: ResourceLoader,
) {
    @Transactional
    fun seed(userId: UUID) {
        if (!userApi.exists(userId)) return
        if (onboardingPathRepository.existsByUserId(userId)) return

        val resources = listOf(
            resourceLoader.getResource("classpath:generic-seed-data.yml"),
            resourceLoader.getResource("classpath:frontend-seed-data.yml"),
            resourceLoader.getResource("classpath:backend-seed-data.yml"),
        )

        val resource = resources.random()

        if (!resource.exists()) return

        val filename = resource.filename ?: return
        val mapper: ObjectMapper = if (filename.endsWith(".yml") || filename.endsWith(".yaml")) {
            YAMLMapper().apply { findAndRegisterModules() }
        } else {
            ObjectMapper().apply { findAndRegisterModules() }
        }

        val seedData = mapper.readValue<SeedData>(resource.inputStream)
        seedData.paths.forEach { pathSeed ->
            val path = OnboardingPath(userId = userId)

            pathSeed.phases.forEach { phaseSeed ->
                val phase = OnboardingPhase(
                    path = path,
                    position = phaseSeed.position,
                    title = phaseSeed.title,
                    description = phaseSeed.description,
                )

                phaseSeed.steps.forEach { stepSeed ->
                    val step = OnboardingStep(
                        phase = phase,
                        position = stepSeed.position,
                        title = stepSeed.title,
                        description = stepSeed.description,
                        type = stepSeed.type,
                        estimatedMinutes = stepSeed.estimatedMinutes,
                        expectedOutcome = stepSeed.expectedOutcome,
                        status = StepStatus.WAITING,
                    )

                    stepSeed.tasks.forEach { taskSeed ->
                        step.tasks.add(
                            OnboardingTask(
                                step = step,
                                position = taskSeed.position,
                                title = taskSeed.title,
                                description = taskSeed.description,
                            ),
                        )
                    }

                    stepSeed.resources.forEach { resourceSeed ->
                        step.resources.add(
                            OnboardingResource(
                                step = step,
                                title = resourceSeed.title,
                                description = resourceSeed.description,
                                url = resourceSeed.url,
                            ),
                        )
                    }

                    phase.steps.add(step)
                }

                path.phases.add(phase)
            }

            onboardingPathRepository.save(path) // CascadeType.ALL takes care of the rest
        }
    }

    @Transactional
    fun reset(userId: UUID) {
        onboardingPathRepository.deleteByUserId(userId)
        onboardingPathRepository.flush()
    }
}
