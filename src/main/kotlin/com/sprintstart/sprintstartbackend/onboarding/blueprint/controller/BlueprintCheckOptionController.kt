package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.CreateBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.DeleteBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.UpdateBlueprintCheckOptionPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.UpdateBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.CreateBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.GetBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.UpdateBlueprintCheckOptionPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.UpdateBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintCheckOptionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/onboarding/blueprints/checks")
class BlueprintCheckOptionAdminController(
    private val blueprintCheckOptionService: BlueprintCheckOptionService,
) {
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/questions/{questionId}/options")
    fun getBlueprintCheckOptionsForQuestion(
        @PathVariable questionId: UUID,
    ): List<GetBlueprintCheckOptionResponse> {
        return blueprintCheckOptionService.getBlueprintCheckOptionsForQuestion(
            BlueprintScope.Global,
            questionId,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/options/{optionId}")
    fun getBlueprintCheckOptionById(
        @PathVariable optionId: UUID,
    ): GetBlueprintCheckOptionResponse {
        return blueprintCheckOptionService.getBlueprintCheckOptionById(
            BlueprintScope.Global,
            optionId,
        )
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/questions/{questionId}/options")
    fun createBlueprintCheckOptionForQuestion(
        @PathVariable questionId: UUID,
        @RequestBody request: CreateBlueprintCheckOptionRequest,
    ): CreateBlueprintCheckOptionResponse {
        return blueprintCheckOptionService.createBlueprintCheckOptionForQuestion(
            BlueprintScope.Global,
            questionId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/options/{optionId}")
    fun updateBlueprintCheckOptionById(
        @PathVariable optionId: UUID,
        @RequestBody request: UpdateBlueprintCheckOptionRequest,
    ): UpdateBlueprintCheckOptionResponse {
        return blueprintCheckOptionService.updateBlueprintCheckOptionById(
            BlueprintScope.Global,
            optionId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/options/{optionId}/position")
    fun updateBlueprintCheckOptionPositionById(
        @PathVariable optionId: UUID,
        @Valid @RequestBody request: UpdateBlueprintCheckOptionPositionRequest,
    ): List<UpdateBlueprintCheckOptionPositionResponse> {
        return blueprintCheckOptionService.updateBlueprintCheckOptionPositionById(
            BlueprintScope.Global,
            optionId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/options/{optionId}")
    fun deleteBlueprintCheckOptionById(
        @PathVariable optionId: UUID,
        @RequestBody request: DeleteBlueprintCheckOptionRequest,
    ) {
        blueprintCheckOptionService.deleteBlueprintCheckOptionById(
            BlueprintScope.Global,
            optionId,
            request,
        )
    }
}

@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints/checks")
class BlueprintCheckOptionController(
    private val blueprintCheckOptionService: BlueprintCheckOptionService,
) {
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @GetMapping("/questions/{questionId}/options")
    fun getBlueprintCheckOptionsForQuestion(
        @PathVariable projectId: UUID,
        @PathVariable questionId: UUID,
    ): List<GetBlueprintCheckOptionResponse> {
        return blueprintCheckOptionService.getBlueprintCheckOptionsForQuestion(
            BlueprintScope.Project(projectId),
            questionId,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @GetMapping("/options/{optionId}")
    fun getBlueprintCheckOptionById(
        @PathVariable projectId: UUID,
        @PathVariable optionId: UUID,
    ): GetBlueprintCheckOptionResponse {
        return blueprintCheckOptionService.getBlueprintCheckOptionById(
            BlueprintScope.Project(projectId),
            optionId,
        )
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PostMapping("/questions/{questionId}/options")
    fun createBlueprintCheckOptionForQuestion(
        @PathVariable projectId: UUID,
        @PathVariable questionId: UUID,
        @RequestBody request: CreateBlueprintCheckOptionRequest,
    ): CreateBlueprintCheckOptionResponse {
        return blueprintCheckOptionService.createBlueprintCheckOptionForQuestion(
            BlueprintScope.Project(projectId),
            questionId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PutMapping("/options/{optionId}")
    fun updateBlueprintCheckOptionById(
        @PathVariable projectId: UUID,
        @PathVariable optionId: UUID,
        @RequestBody request: UpdateBlueprintCheckOptionRequest,
    ): UpdateBlueprintCheckOptionResponse {
        return blueprintCheckOptionService.updateBlueprintCheckOptionById(
            BlueprintScope.Project(projectId),
            optionId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PutMapping("/options/{optionId}/position")
    fun updateBlueprintCheckOptionPositionById(
        @PathVariable projectId: UUID,
        @PathVariable optionId: UUID,
        @Valid @RequestBody request: UpdateBlueprintCheckOptionPositionRequest,
    ): List<UpdateBlueprintCheckOptionPositionResponse> {
        return blueprintCheckOptionService.updateBlueprintCheckOptionPositionById(
            BlueprintScope.Project(projectId),
            optionId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @DeleteMapping("/options/{optionId}")
    fun deleteBlueprintCheckOptionById(
        @PathVariable projectId: UUID,
        @PathVariable optionId: UUID,
        @RequestBody request: DeleteBlueprintCheckOptionRequest,
    ) {
        blueprintCheckOptionService.deleteBlueprintCheckOptionById(
            BlueprintScope.Project(projectId),
            optionId,
            request,
        )
    }
}
