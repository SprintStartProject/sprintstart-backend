package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardStructure
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BoardStructureRepository : JpaRepository<BoardStructure, UUID> {
    fun findByBoardId(boardId: UUID): BoardStructure?

    fun deleteAllByBoardIdIn(boardIds: Collection<UUID>)
}
