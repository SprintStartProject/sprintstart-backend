package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModuleStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.onboarding.model.request.module.CreateCompetencyModuleRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.module.CreateModulePageRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.module.RejectCompetencyModuleRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.module.ReorderModulePagesRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.module.UpdateCompetencyModuleRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.module.UpdateModulePageRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.verification.UpsertVerificationRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.module.CompetencyModuleResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.module.CompetencyModulesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.module.ModulePageResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.verification.VerificationResponse
import com.sprintstart.sprintstartbackend.onboarding.service.CompetencyModuleService
import com.sprintstart.sprintstartbackend.onboarding.service.VerificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.flow.Flow
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
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
 * PM authoring surface for the content a competency teaches.
 *
 * Reads are open to HR as well (they review onboarding without owning it); every write is
 * `ADMIN`/`PM`, because approving a module changes what every hire in the project sees.
 */
@RestController
@RequestMapping("/api/v1/onboarding/competency-modules")
@Tag(name = "Onboarding - Competency modules", description = "Author the shared module a competency teaches")
@Suppress("TooManyFunctions")
class CompetencyModuleController(
    private val competencyModuleService: CompetencyModuleService,
    private val verificationService: VerificationService,
) {
    @Operation(
        summary = "List modules",
        description = "Lists a project's modules in one lifecycle state",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Modules returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun list(
        @RequestParam projectId: UUID,
        @RequestParam(required = false, defaultValue = "ACTIVE") status: ModuleStatus,
    ): CompetencyModulesResponse = competencyModuleService.list(projectId, status)

    @Operation(summary = "Get a module", description = "Returns one module with its ordered pages")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Module returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No module found with the given id"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun get(
        @PathVariable id: UUID,
    ): CompetencyModuleResponse = competencyModuleService.get(id)

    @Operation(
        summary = "Start a module version",
        description = "Creates a new DRAFT version for one competency in one project",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Draft module created"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No competency found with the given key"),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun create(
        @RequestBody request: CreateCompetencyModuleRequest,
    ): CompetencyModuleResponse = competencyModuleService.create(request)

    @Operation(
        summary = "Draft a module with the AI",
        description = "Asks the AI to draft the module for one competency from the project's corpus",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Proposal stored, or nothing to propose"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No competency found with the given key"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/propose")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    suspend fun proposeFromCorpus(
        @RequestParam competencyKey: String,
        @RequestParam projectId: UUID,
    ): CompetencyModuleResponse? = competencyModuleService.proposeFromCorpus(competencyKey, projectId)

    @Operation(
        summary = "Watch a module being drafted (streaming)",
        description = "The same draft as `POST /propose`, streamed as Server-Sent Events so a PM can " +
            "watch it build: retrieve/generate/ground stages and a page as it clears grounding, then " +
            "a terminal `done` once the proposal is stored. The stored proposal is identical to the " +
            "non-streaming path — the stream is a view, so a client reloads the module list on `done`.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Progress stream started"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No competency found with the given key"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/propose/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    suspend fun streamProposeFromCorpus(
        @RequestParam competencyKey: String,
        @RequestParam projectId: UUID,
    ): Flow<AiProgressEvent> = competencyModuleService.streamProposalFromCorpus(competencyKey, projectId)

    @Operation(summary = "Edit a module", description = "Updates a draft module's title or summary")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Module updated"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No module found with the given id"),
            ApiResponse(responseCode = "409", description = "The module is live or archived"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: UpdateCompetencyModuleRequest,
    ): CompetencyModuleResponse = competencyModuleService.update(id, request)

    @Operation(summary = "Propose a module", description = "Offers a draft module for review")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Module proposed"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No module found with the given id"),
            ApiResponse(responseCode = "409", description = "The module is not a draft"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/{id}/propose")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun propose(
        @PathVariable id: UUID,
    ): CompetencyModuleResponse = competencyModuleService.propose(id)

    @Operation(
        summary = "Publish a module",
        description = "Makes this version live for its competency and project, archiving the previous one",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Module published"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No module found with the given id"),
            ApiResponse(responseCode = "409", description = "The module is archived, or has no pages"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun approve(
        @PathVariable id: UUID,
    ): CompetencyModuleResponse = competencyModuleService.approve(id)

    @Operation(summary = "Reject a module", description = "Archives a version without publishing it")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Module archived"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No module found with the given id"),
            ApiResponse(responseCode = "409", description = "The module is the live one"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun reject(
        @PathVariable id: UUID,
        @RequestBody request: RejectCompetencyModuleRequest,
    ): CompetencyModuleResponse = competencyModuleService.reject(id, request.reason)

    @Operation(summary = "Add a page", description = "Appends or inserts a page into a draft module")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Page created"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No module found with the given id"),
            ApiResponse(responseCode = "409", description = "The module is live or archived"),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{id}/pages")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun addPage(
        @PathVariable id: UUID,
        @RequestBody request: CreateModulePageRequest,
    ): ModulePageResponse = competencyModuleService.addPage(id, request)

    @Operation(summary = "Edit a page", description = "Updates one page's kind, title or body")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Page updated"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No page found with the given id"),
            ApiResponse(responseCode = "409", description = "The module is live or archived"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PatchMapping("/pages/{pageId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun updatePage(
        @PathVariable pageId: UUID,
        @RequestBody request: UpdateModulePageRequest,
    ): ModulePageResponse = competencyModuleService.updatePage(pageId, request)

    @Operation(summary = "Delete a page", description = "Removes one page from a draft module")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Page deleted"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No page found with the given id"),
            ApiResponse(responseCode = "409", description = "The module is live or archived"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/pages/{pageId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun deletePage(
        @PathVariable pageId: UUID,
    ) = competencyModuleService.deletePage(pageId)

    @Operation(
        summary = "Reorder pages",
        description = "Applies a complete new page order in one call",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Pages reordered"),
            ApiResponse(responseCode = "400", description = "The id list is not exactly the module's pages"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No module found with the given id"),
            ApiResponse(responseCode = "409", description = "The module is live or archived"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/{id}/pages/order")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun reorderPages(
        @PathVariable id: UUID,
        @RequestBody request: ReorderModulePagesRequest,
    ): CompetencyModuleResponse = competencyModuleService.reorderPages(id, request)

    @Operation(
        summary = "Configure a module's check",
        description = "Creates or replaces the graded gate a hire must pass to master the competency",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Check saved"),
            ApiResponse(responseCode = "400", description = "A type-required field is missing"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No module or competency found"),
            ApiResponse(responseCode = "409", description = "The module is live or archived"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/{id}/verification")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun upsertVerification(
        @PathVariable id: UUID,
        @RequestBody request: UpsertVerificationRequest,
    ): VerificationResponse = verificationService.upsertModuleVerification(id, request)
}
