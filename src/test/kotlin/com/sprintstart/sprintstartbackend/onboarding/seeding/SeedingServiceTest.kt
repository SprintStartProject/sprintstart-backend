package com.sprintstart.sprintstartbackend.onboarding.seeding

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import java.util.UUID

class SeedingServiceTest {
    private val onboardingPathRepository: OnboardingPathRepository = mockk()
    private val userApi: UserApi = mockk()
    private val resourceLoader: ResourceLoader = mockk()

    private val seedingService = SeedingService(
        onboardingPathRepository = onboardingPathRepository,
        userApi = userApi,
        resourceLoader = resourceLoader,
    )

    @Test
    fun `seed should do nothing if user does not exist`() {
        val userId = UUID.randomUUID()

        every {
            userApi.exists(userId)
        } returns false

        seedingService.seed(userId)

        verify(exactly = 1) {
            userApi.exists(userId)
        }

        verify(exactly = 0) {
            onboardingPathRepository.existsByUserId(any())
            onboardingPathRepository.save(any())
            resourceLoader.getResource(any())
        }
    }

    @Test
    fun `seed should do nothing if onboarding path already exists for user`() {
        val userId = UUID.randomUUID()

        every {
            userApi.exists(userId)
        } returns true

        every {
            onboardingPathRepository.existsByUserId(userId)
        } returns true

        seedingService.seed(userId)

        verify(exactly = 1) {
            userApi.exists(userId)
            onboardingPathRepository.existsByUserId(userId)
        }

        verify(exactly = 0) {
            onboardingPathRepository.save(any())
            resourceLoader.getResource(any())
        }
    }

    @Test
    fun `seed should do nothing if selected seed resource does not exist`() {
        val userId = UUID.randomUUID()

        every {
            userApi.exists(userId)
        } returns true

        every {
            onboardingPathRepository.existsByUserId(userId)
        } returns false

        every {
            resourceLoader.getResource(any())
        } returns nonExistingResource()

        seedingService.seed(userId)

        verify(exactly = 1) {
            userApi.exists(userId)
            onboardingPathRepository.existsByUserId(userId)
        }

        verify(exactly = 3) {
            resourceLoader.getResource(any())
        }

        verify(exactly = 0) {
            onboardingPathRepository.save(any())
        }
    }

    @Test
    fun `seed should create onboarding path from seed data`() {
        val userId = UUID.randomUUID()
        val savedPath = slot<OnboardingPath>()

        every {
            userApi.exists(userId)
        } returns true

        every {
            onboardingPathRepository.existsByUserId(userId)
        } returns false

        every {
            resourceLoader.getResource(any())
        } returns existingYamlResource()

        every {
            onboardingPathRepository.save(capture(savedPath))
        } answers {
            savedPath.captured
        }

        seedingService.seed(userId)

        verify(exactly = 1) {
            onboardingPathRepository.save(any())
        }

        val path = savedPath.captured

        assertThat(path.userId).isEqualTo(userId)
        assertThat(path.phases).hasSize(1)

        val phase = path.phases.first()
        assertThat(phase.path).isSameAs(path)
        assertThat(phase.position).isEqualTo(1)
        assertThat(phase.title).isEqualTo("Setup")
        assertThat(phase.description).isEqualTo("Setup phase")
        assertThat(phase.steps).hasSize(1)

        val step = phase.steps.first()
        assertThat(step.phase).isSameAs(phase)
        assertThat(step.position).isEqualTo(1)
        assertThat(step.title).isEqualTo("Install tools")
        assertThat(step.description).isEqualTo("Install required tools")
        assertThat(step.type).isEqualTo(StepType.VIDEO)
        assertThat(step.estimatedMinutes).isEqualTo(30)
        assertThat(step.expectedOutcome).isEqualTo("Tools are installed")
        assertThat(step.status).isEqualTo(StepStatus.WAITING)
        assertThat(step.tasks).hasSize(1)
        assertThat(step.resources).hasSize(1)

        val task = step.tasks.first()
        assertThat(task.step).isSameAs(step)
        assertThat(task.position).isEqualTo(1)
        assertThat(task.title).isEqualTo("Install IntelliJ")
        assertThat(task.description).isEqualTo("Install IntelliJ IDEA")

        val resource = step.resources.first()
        assertThat(resource.step).isSameAs(step)
        assertThat(resource.title).isEqualTo("IntelliJ Download")
        assertThat(resource.description).isEqualTo("Download page")
        assertThat(resource.url).isEqualTo("https://www.jetbrains.com/idea/")
    }

    @Test
    fun `reset should delete onboarding path by user id and flush repository`() {
        val userId = UUID.randomUUID()

        every {
            onboardingPathRepository.deleteByUserId(userId)
        } just runs

        every {
            onboardingPathRepository.flush()
        } just runs

        seedingService.reset(userId)

        verify(exactly = 1) {
            onboardingPathRepository.deleteByUserId(userId)
            onboardingPathRepository.flush()
        }
    }

    private fun existingYamlResource(): Resource {
        return object : ByteArrayResource(seedYaml().toByteArray()) {
            override fun getFilename(): String {
                return "test-seed-data.yml"
            }

            override fun exists(): Boolean {
                return true
            }
        }
    }

    private fun nonExistingResource(): Resource {
        return object : ByteArrayResource(ByteArray(0)) {
            override fun getFilename(): String {
                return "missing-seed-data.yml"
            }

            override fun exists(): Boolean {
                return false
            }
        }
    }

    private fun seedYaml(): String {
        return """
            paths:
              - userId: ignored-by-service
                phases:
                  - position: 1
                    title: Setup
                    description: Setup phase
                    steps:
                      - position: 1
                        title: Install tools
                        description: Install required tools
                        type: VIDEO
                        estimatedMinutes: 30
                        expectedOutcome: Tools are installed
                        tasks:
                          - position: 1
                            title: Install IntelliJ
                            description: Install IntelliJ IDEA
                        resources:
                          - title: IntelliJ Download
                            description: Download page
                            url: https://www.jetbrains.com/idea/
            """.trimIndent()
    }
}
