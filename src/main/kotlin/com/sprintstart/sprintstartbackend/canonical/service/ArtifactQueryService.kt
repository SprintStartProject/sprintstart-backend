package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.dto.response.ArtifactPageResponse
import com.sprintstart.sprintstartbackend.canonical.model.dto.response.PageMetadata
import com.sprintstart.sprintstartbackend.canonical.model.entity.Artifact
import com.sprintstart.sprintstartbackend.canonical.model.mapper.ArtifactMapper
import com.sprintstart.sprintstartbackend.canonical.repository.ArtifactRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class ArtifactQueryService(
    private val artifactRepository: ArtifactRepository,
    private val artifactMapper: ArtifactMapper,
) {
    fun getAllArtifacts(page: Int, size: Int, filter: String): ArtifactPageResponse {
        val pageable = PageRequest.of(
            page - 1,
            size,
            Sort.by("ingestedAt").descending(),
        )

        val result: Page<Artifact> =
            if (filter.isBlank()) {
                artifactRepository.findAll(pageable)
            } else {
                artifactRepository.search(filter.trim(), pageable)
            }
        return ArtifactPageResponse(
            items = result.content.map { artifactMapper.toResponse(it) },
            page = PageMetadata(
                number = page.toLong(),
                size = size.toLong(),
                totalElements = result.totalElements,
                totalPages = result.totalPages.toLong(),
                hasNext = result.hasNext(),
                hasPrevious = result.hasPrevious(),
            ),
        )
    }
}
