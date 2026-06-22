package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.CreateOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.ReviewOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.UpdateOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.CreateOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetAllOnboardingSkipsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.ReviewOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.UpdateOnboardingStepResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
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
@RequestMapping("/api/v1")
class OnboardingSkipController {
//  ========================== Endpoints for users (/me/...) ==========================

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/onboarding/me/skips")
    @PreAuthorize("hasRole('USER')")
    fun getAllSkipsForMe(): List<GetOnboardingSkipResponse> {
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/onboarding/me/steps/{stepId}/skips")
    @PreAuthorize("hasRole('USER')")
    fun getSkipsByStepIdForMe(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable stepId: UUID,
    ): List<GetOnboardingSkipResponse> {
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/onboarding/me/skips/{skipId}")
    @PreAuthorize("hasRole('USER')")
    fun getSkipByIdForMe(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable skipId: UUID,
    ): GetOnboardingSkipResponse {
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/onboarding/me/steps/{stepId}/skips")
    @PreAuthorize("hasRole('USER')")
    fun createSkipAtStepForMe(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable stepId: UUID,
        @Valid @RequestBody request: CreateOnboardingSkipRequest,
    ): CreateOnboardingSkipResponse {
    }

    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/onboarding/me/skips/{skipId}")
    @PreAuthorize("hasRole('USER')")
    fun updateSkipByIdForMe(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable skipId: UUID,
        @Valid @RequestBody request: UpdateOnboardingSkipRequest,
    ): UpdateOnboardingStepResponse {
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/onboarding/me/skips/{skipId}")
    @PreAuthorize("hasRole('USER')")
    fun deleteSkipByIdForMe(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable skipId: UUID,
    ) {
    }

//  ========================== Endpoints for admins ==========================

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/admin/onboarding/skips")
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllSkips(): List<GetAllOnboardingSkipsResponse> {
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/admin/onboarding/users/{usersId}/skips")
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllSkips(
        @PathVariable usersId: UUID,
    ): List<GetOnboardingSkipResponse> {
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/admin/onboarding/steps/{stepId}/skips")
    @PreAuthorize("hasRole('ADMIN')")
    fun getSkipsForStepId(
        @PathVariable stepId: UUID,
    ): List<GetOnboardingSkipResponse> {
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/admin/onboarding/skips/{skipId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getSkipById(
        @PathVariable skipId: UUID,
    ): GetOnboardingSkipResponse {
    }

    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/admin/onboarding/skips/{skipId}/accept")
    @PreAuthorize("hasRole('ADMIN')")
    fun acceptSkipById(
        @PathVariable skipId: UUID,
        @Valid @RequestBody request: ReviewOnboardingSkipRequest,
    ): ReviewOnboardingSkipResponse {
    }

    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/admin/onboarding/skips/{skipId}/deny")
    @PreAuthorize("hasRole('ADMIN')")
    fun denySkipById(
        @PathVariable skipId: UUID,
        @Valid @RequestBody request: ReviewOnboardingSkipRequest,
    ): ReviewOnboardingSkipResponse {
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/admin/onboarding/skips/{skipId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteSkipById(
        @PathVariable skipId: UUID,
    ) {
    }
}
