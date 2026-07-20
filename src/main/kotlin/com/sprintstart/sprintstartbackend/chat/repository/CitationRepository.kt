package com.sprintstart.sprintstartbackend.chat.repository

import com.sprintstart.sprintstartbackend.chat.models.Citation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
internal interface CitationRepository : JpaRepository<Citation, UUID>
