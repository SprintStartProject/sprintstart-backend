package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ArrivalDerivation
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ArrivalTeamToolsTest {
    private val arrivalStepService: ArrivalStepService = mockk()
    private val tools = ArrivalTeamTools(arrivalStepService)

    private val projectId = UUID.randomUUID()
    private val context = TeamToolContext(userId = UUID.randomUUID(), authId = "auth|pm", projectId = projectId)

    private fun call(name: String) = BuddyToolCallDto(id = "c1", name = name)

    private fun step(
        key: String,
        title: String,
        description: String? = null,
        href: String? = null,
        position: Int = 0,
        settledBy: Rigor = Rigor.DECLARED,
        selfConfirmable: Boolean = true,
    ) = ArrivalStep(
        key = key,
        projectId = projectId,
        title = title,
        description = description,
        href = href,
        position = position,
        settledBy = settledBy,
        selfConfirmable = selfConfirmable,
    )

    @Test
    fun `is the arrival area and handles exactly its two reads`() {
        assertThat(tools.area).isEqualTo(TeamArea.ARRIVAL)
        assertThat(tools.toolSpecs().map { it.name })
            .containsExactly(ArrivalTeamTools.LIST_ARRIVAL_STEPS, ArrivalTeamTools.LIST_DERIVABLE_STEPS)
        assertThat(tools.handles("create_arrival_steps")).isFalse()
        assertThat(tools.handles(ArrivalTeamTools.LIST_ARRIVAL_STEPS)).isTrue()
    }

    @Test
    fun `lists the project's steps in list order with the key to act on`() {
        every { arrivalStepService.listForAuthoring(projectId) } returns listOf(
            step("laptop", "Collect your laptop", description = "Ask ops.", position = 0),
            step("vpn", "Get on the VPN", href = "https://example.test/vpn", position = 1),
        )

        val result = tools.execute(call(ArrivalTeamTools.LIST_ARRIVAL_STEPS), context)

        assertThat(result).contains("Collect your laptop [key: laptop]")
        assertThat(result).contains("Ask ops.")
        assertThat(result).contains("Get on the VPN [key: vpn]")
        assertThat(result).contains("Link: https://example.test/vpn")
        assertThat(result.indexOf("laptop")).isLessThan(result.indexOf("vpn"))
    }

    @Test
    fun `says how each step settles, in words rather than the enum`() {
        every { arrivalStepService.listForAuthoring(projectId) } returns listOf(
            step("gh", "GitHub", settledBy = Rigor.OBSERVED, selfConfirmable = false),
            step("badge", "Badge", settledBy = Rigor.ATTESTED),
        )

        val result = tools.execute(call(ArrivalTeamTools.LIST_ARRIVAL_STEPS), context)

        assertThat(result).contains("settled by the system observing it")
        assertThat(result).contains("the hire cannot tick this one themselves")
        assertThat(result).contains("settled by somebody else attesting it")
        assertThat(result).doesNotContain("OBSERVED")
        assertThat(result).doesNotContain("ATTESTED")
    }

    @Test
    fun `an empty list is not presented as anything being blocked`() {
        every { arrivalStepService.listForAuthoring(projectId) } returns emptyList()

        val result = tools.execute(call(ArrivalTeamTools.LIST_ARRIVAL_STEPS), context)

        assertThat(result).contains("no arrival steps yet")
        assertThat(result).contains("Nothing is blocked by that")
    }

    @Test
    fun `answers derivable steps for this project, never the default list`() {
        every { arrivalStepService.derivable(projectId) } returns listOf(
            ArrivalDerivation.GITHUB_ACCOUNT to true,
            ArrivalDerivation.ENVIRONMENT_READY to false,
        )

        val result = tools.execute(call(ArrivalTeamTools.LIST_DERIVABLE_STEPS), context)

        assertThat(result).contains("[key: github-account]")
        assertThat(result).contains("already on this project's list")
        assertThat(result).contains("[key: environment-ready]")
        assertThat(result).contains("not on this project's list")
        // The no-argument overload answers from the organisation-wide list, which team mode must not read.
        verify(exactly = 0) { arrivalStepService.derivable() }
    }

    @Test
    fun `both reads are scoped to the turn's project and take no arguments`() {
        every { arrivalStepService.listForAuthoring(projectId) } returns emptyList()
        every { arrivalStepService.derivable(projectId) } returns emptyList()

        tools.execute(call(ArrivalTeamTools.LIST_ARRIVAL_STEPS), context)
        tools.execute(call(ArrivalTeamTools.LIST_DERIVABLE_STEPS), context)

        verify { arrivalStepService.listForAuthoring(projectId) }
        verify { arrivalStepService.derivable(projectId) }
        verify(exactly = 0) { arrivalStepService.listForAuthoring(null) }
        assertThat(tools.toolSpecs().map { it.parameters["properties"].toString() })
            .allMatch { it == "{}" }
    }

    @Test
    fun `the list tool's description keeps the not-a-gate rules`() {
        val description = ArrivalTeamTools.LIST_ARRIVAL_STEPS_SPEC.description

        assertThat(description).contains("never blocks anybody")
        assertThat(description).contains("never totalled")
    }

    @Test
    fun `an unknown tool name is answered, not thrown`() {
        assertThat(tools.execute(call("nope"), context)).isEqualTo("Unknown tool: nope.")
    }
}
