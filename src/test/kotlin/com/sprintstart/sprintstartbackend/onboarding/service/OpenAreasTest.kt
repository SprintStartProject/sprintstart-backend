package com.sprintstart.sprintstartbackend.onboarding.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenAreasTest {
    @Test
    fun `an area that was carried over is mounted but not recorded as opened this turn`() {
        val areas = OpenAreas(carriedOver = setOf(TeamArea.KNOWLEDGE))

        assertThat(areas.mounted).containsExactly(TeamArea.KNOWLEDGE)
        assertThat(areas.openedThisTurn).isEmpty()
    }

    @Test
    fun `opening an area mounts it and records it, whether or not it was already there`() {
        val areas = OpenAreas(carriedOver = setOf(TeamArea.KNOWLEDGE))

        areas.open(TeamArea.KNOWLEDGE)
        areas.open(TeamArea.ARRIVAL)

        assertThat(areas.mounted).containsExactlyInAnyOrder(TeamArea.KNOWLEDGE, TeamArea.ARRIVAL)
        assertThat(areas.openedThisTurn).containsExactlyInAnyOrder(TeamArea.KNOWLEDGE, TeamArea.ARRIVAL)
    }

    @Test
    fun `areas are stored by name, sorted, and read back`() {
        val stored = setOf(TeamArea.STARTER_WORK, TeamArea.ARRIVAL).encoded()

        assertThat(stored).isEqualTo("ARRIVAL,STARTER_WORK")
        assertThat(stored.toTeamAreas()).containsExactlyInAnyOrder(TeamArea.ARRIVAL, TeamArea.STARTER_WORK)
    }

    @Test
    fun `no areas is stored as nothing, and nothing reads back as no areas`() {
        assertThat(emptySet<TeamArea>().encoded()).isNull()
        assertThat(null.toTeamAreas()).isEmpty()
        assertThat("".toTeamAreas()).isEmpty()
    }

    @Test
    fun `a stored name that is no longer an area is dropped rather than failing the turn`() {
        assertThat("KNOWLEDGE, NOT_AN_AREA ,".toTeamAreas()).containsExactly(TeamArea.KNOWLEDGE)
    }

    @Test
    fun `every area has a summary the model can choose it by`() {
        TeamArea.entries.forEach {
            assertThat(it.summary).describedAs(it.name).isNotBlank()
        }
    }
}
