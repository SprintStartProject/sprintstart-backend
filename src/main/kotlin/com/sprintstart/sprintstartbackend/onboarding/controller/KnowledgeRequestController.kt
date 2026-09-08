package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.knowledge.AnswerKnowledgeRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.knowledge.CreateKnowledgeRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.knowledge.UpdateCanonicalAnswer
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.CanonicalAnswerResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.KnowledgeRequestResponse
import com.sprintstart.sprintstartbackend.onboarding.service.KnowledgeBaseService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * The buddy's growth loop: a hire escalates a question the buddy could not answer, a PM answers it,
 * and the answer becomes durable knowledge the buddy serves next time.
 *
 * Two audiences. A hire escalates and reads their own questions (`/me/...`). Whoever runs the
 * project works the inbox and authors answers — PM/ADMIN write, HR reads, matching every other
 * PM surface.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Knowledge",
    description = "Escalating gaps to a person and keeping their answers as buddy knowledge",
)
class KnowledgeRequestController(
    private val knowledgeBaseService: KnowledgeBaseService,
) {
    @Operation(
        summary = "Flag a question to my PM",
        description = "Escalate something the buddy could not answer. The hire chooses to send it.",
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/me/knowledge-requests")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun escalate(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: CreateKnowledgeRequest,
    ): KnowledgeRequestResponse =
        knowledgeBaseService.escalate(jwt.subject, request.projectId, request.question)

    @Operation(
        summary = "My escalated questions",
        description = "The questions I sent to a person, newest first, each with its answer once given.",
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/knowledge-requests")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun listMine(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ): List<KnowledgeRequestResponse> = knowledgeBaseService.listMine(jwt.subject)

    @Operation(
        summary = "The escalation inbox",
        description = "Open questions on a project waiting on a person, longest-waiting first.",
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/knowledge-requests")
    @PreAuthorize("hasAnyRole('PM', 'HR', 'ADMIN')")
    fun listOpen(@RequestParam projectId: UUID): List<KnowledgeRequestResponse> =
        knowledgeBaseService.listOpen(projectId)

    @Operation(
        summary = "Answer an escalated question",
        description = "Mints a durable answer the buddy will serve, and closes the request against it.",
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/knowledge-requests/{requestId}/answer")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    fun answer(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable requestId: UUID,
        @RequestBody request: AnswerKnowledgeRequest,
    ): CanonicalAnswerResponse =
        knowledgeBaseService.answer(jwt.subject, requestId, request.answer, request.question)

    @Operation(
        summary = "Dismiss an escalated question",
        description = "Close a one-off or duplicate without minting a durable answer.",
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/knowledge-requests/{requestId}/dismiss")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    fun dismiss(@PathVariable requestId: UUID) {
        knowledgeBaseService.dismiss(requestId)
    }

    @Operation(
        summary = "Canonical answers on a project",
        description = "Every durable answer, for a PM to review, reuse, or keep current.",
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/canonical-answers")
    @PreAuthorize("hasAnyRole('PM', 'HR', 'ADMIN')")
    fun listAnswers(@RequestParam projectId: UUID): List<CanonicalAnswerResponse> =
        knowledgeBaseService.listAnswers(projectId)

    @Operation(
        summary = "Edit a canonical answer",
        description = "Update a durable answer when reality changes; the buddy serves the new text.",
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/canonical-answers/{answerId}")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    fun editAnswer(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable answerId: UUID,
        @RequestBody request: UpdateCanonicalAnswer,
    ): CanonicalAnswerResponse =
        knowledgeBaseService.editAnswer(jwt.subject, answerId, request.question, request.answer)
}
