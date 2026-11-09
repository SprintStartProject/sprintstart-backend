package com.sprintstart.sprintstartbackend.path.repository

import com.sprintstart.sprintstartbackend.path.model.entity.SkipRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SkipRequestRepository : JpaRepository<SkipRequest, UUID>
