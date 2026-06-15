package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.dto.response.SourceIngestionStatusResponse
import org.springframework.stereotype.Service

@Service
class IngestionStatusService(

) {
    fun getIngestionStatusPerSource()
    : List<SourceIngestionStatusResponse>{
        return null
    }
  }