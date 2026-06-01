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

/**
 * Provides development and demo seeding operations for onboarding data.
 *
 * This service creates onboarding paths from predefined seed files and can remove
 * all onboarding data for a given user. The seed operation is intentionally
 * defensive: it only creates data if the user exists, if the user does not already
 * have onboarding data, and if the selected seed resource is available.
 *
 * The seeded data is loaded from classpath resources and mapped into the onboarding
 * entity graph. Persisting the root [OnboardingPath] is enough because the nested
 * onboarding entities are expected to be persisted through cascading.
 *
 * @property onboardingPathRepository Repository used to read, create, and delete onboarding paths.
 * @property userApi Module-facing API used to check whether a user exists.
 * @property resourceLoader Loader used to access seed files from the classpath.
 */
@Suppress("ReturnCount")
@Service
class SeedingService(
    private val onboardingPathRepository: OnboardingPathRepository,
    private val userApi: UserApi,
    private val resourceLoader: ResourceLoader,
) {
    /**
     * Seeds onboarding data for the given user.
     *
     * The method exits without creating data if:
     * - the user does not exist,
     * - the user already has an onboarding path,
     * - the selected seed file does not exist,
     * - or the selected seed file has no filename.
     *
     * If seeding is possible, one of the configured seed files is selected randomly,
     * parsed as YAML or JSON depending on the file extension, and converted into
     * onboarding path, phase, step, task, and resource entities.
     *
     * All seeded steps are initialized with [StepStatus.WAITING].
     *
     * @param userId The unique identifier of the user for whom onboarding data should be created.
     */
    @Suppress("LongMethod")
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

    /**
     * Deletes all onboarding data associated with the given user.
     *
     * The repository is flushed immediately after deletion so the reset is executed
     * within the current transaction before the method returns. This is useful for
     * development and test flows where seed data may be recreated right after reset.
     *
     * @param userId The unique identifier of the user whose onboarding data should be deleted.
     */
    @Transactional
    fun reset(userId: UUID) {
        onboardingPathRepository.deleteByUserId(userId)
        onboardingPathRepository.flush()
    }
}
