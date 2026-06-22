package com.sprintstart.sprintstartbackend.github.controller

import com.sprintstart.sprintstartbackend.github.models.api.requests.AddPatRequest
import com.sprintstart.sprintstartbackend.github.models.api.requests.GetPatRequest
import com.sprintstart.sprintstartbackend.github.models.api.requests.RemovePatRequest
import com.sprintstart.sprintstartbackend.github.models.api.requests.UpdatePatNameRequest
import com.sprintstart.sprintstartbackend.github.models.api.requests.UpdatePatRequest
import com.sprintstart.sprintstartbackend.github.service.GithubUserService
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/github/pat")
class GithubUserController(
    private val githubUserService: GithubUserService,
) {
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    fun getAllPats(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
    ): ResponseEntity<List<String>> {
        val pats = githubUserService.getAllPATs(jwt.subject)
        return ResponseEntity.ok(pats)
    }

    @GetMapping("/{name}")
    @PreAuthorize("hasRole('USER')")
    fun getPat(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
        @PathVariable
        name: String,
    ): ResponseEntity<String> {
        val pat = githubUserService.getPAT(jwt.subject, GetPatRequest(name))
        return ResponseEntity.ok(pat)
    }

    @PostMapping()
    fun addPat(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
        @Valid
        @RequestBody
        request: AddPatRequest,
    ): ResponseEntity<Unit> {
        githubUserService.addPAT(jwt.subject, request)
        return ResponseEntity.ok().build()
    }

    @PutMapping("/update")
    @PreAuthorize("hasRole('USER')")
    fun updatePat(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
        @Valid
        @RequestBody
        request: UpdatePatRequest,
    ): ResponseEntity<Unit> {
        githubUserService.updatePAT(jwt.subject, request)
        return ResponseEntity.ok().build()
    }

    @PutMapping("/update/name")
    @PreAuthorize("hasRole('USER')")
    fun updatePatName(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
        @Valid
        @RequestBody
        request: UpdatePatNameRequest,
    ): ResponseEntity<Unit> {
        githubUserService.updatePATName(jwt.subject, request)
        return ResponseEntity.ok().build()
    }

    @PutMapping("/delete")
    @PreAuthorize("hasRole('USER')")
    fun deletePat(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
        @Valid
        @RequestBody
        request: RemovePatRequest,
    ): ResponseEntity<Unit> {
        githubUserService.removePAT(jwt.subject, request)
        return ResponseEntity.ok().build()
    }
}
