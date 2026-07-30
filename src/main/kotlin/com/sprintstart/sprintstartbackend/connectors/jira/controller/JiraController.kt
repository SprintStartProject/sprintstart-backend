package com.sprintstart.sprintstartbackend.connectors.jira.controller

import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.ConnectJiraInstanceRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.UpdateJiraInstanceRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraInstanceDto
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.UpdateJiraInstanceResponse
import com.sprintstart.sprintstartbackend.connectors.jira.service.JiraService
import com.sprintstart.sprintstartbackend.connectors.jira.service.JiraUpdateService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(
    name = "Jira Connector",
    description = "Endpoints for interacting with the Jira connector",
)
@Validated
@RestController
@RequestMapping("/api/v1/jira")
internal class JiraController(
    private val service: JiraService,
    private val updateService: JiraUpdateService,
) {
    /**
     * Retrieves a list of connected Jira instances.
     *
     * @param projectId an optional unique identifier of the project to filter connected Jira instances.
     *                  If null, all connected Jira instances will be retrieved.
     * @return a ResponseEntity containing a list of JiraInstanceDto representing the connected Jira instances.
     */
    @Operation(
        summary = "Retrieves a list of connected Jira instances",
        description = "Retrieves a list of connected Jira instances.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Retrieved Jira instances successfully.",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/instances")
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
    fun getInstances(@RequestParam(required = false) projectId: UUID?): ResponseEntity<List<JiraInstanceDto>> {
        val response = if (projectId != null) {
            service.getInstances(projectId)
        } else {
            service.getInstances()
        }
        return ResponseEntity.ok(response)
    }

    /**
     * Connects a Jira instance to the Jira connector.
     *
     * This process will initialize the fetching of all issues from all projects within the specified instance.
     *
     * @param request an instance of [ConnectJiraInstanceRequest] containing details required to connect the Jira
     *                instance.
     * @return a [ResponseEntity] with an empty body and HTTP status 202 (Accepted) if the connection request is
     *         successfully initiated.
     */
    @Operation(
        summary = "Connect a jira instance to the jira connector",
        description =
            "Connects a jira instance to the jira connector." +
                " This will fetch all issues from all projects of that instance.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "Connection request accepted - Initialization jobs started",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(responseCode = "404", description = "Requested Jira instance does not exist"),
            ApiResponse(
                responseCode = "502",
                description = "Requested Jira instance's server capabilities are not compatible",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/connect")
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
    suspend fun connectInstance(@RequestBody @Valid request: ConnectJiraInstanceRequest): ResponseEntity<Unit> {
        service.connectInstanceIfNeeded(request)
        return ResponseEntity.accepted().build()
    }

    /**
     * Updates a Jira instance, fetching all new issues and updated issues.
     *
     * This operation initiates the update process for a specific Jira instance identified by its instance URL.
     * It triggers the fetching of new and updated issues, ensuring the instance remains in sync.
     *
     * @param request an instance of [UpdateJiraInstanceRequest] containing the necessary details
     *                to identify the Jira instance to be updated and associated configurations.
     * @return a [ResponseEntity] containing an [UpdateJiraInstanceResponse] with details about the update operation,
     *         including status and any relevant metadata.
     */
    @Operation(
        summary = "Updates a Jira instance",
        description = "Updates a Jira instance, fetching all new issues & updated issues",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Update request accepted - Initialization jobs started",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(responseCode = "404", description = "Instance or credentials to update not found"),
        ],
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/update")
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
    suspend fun updateInstance(
        @RequestBody @Valid request: UpdateJiraInstanceRequest,
    ): ResponseEntity<UpdateJiraInstanceResponse> {
        val response = updateService.updateJiraInstance(request.instanceUrl, true)
        return ResponseEntity.accepted().body(response)
    }

    /**
     * Updates all connected Jira instances by fetching new and updated issues.
     *
     * This method triggers the update process for all Jira instances that are connected to the system.
     * It starts initialization jobs to retrieve any new or modified issues from each instance.
     * The endpoint requires specific roles for access.
     *
     * @return a ResponseEntity containing a list of UpdateJiraInstanceResponse objects, indicating
     *         the status or result of the update process for each Jira instance.
     */
    @Operation(
        summary = "Updates all Jira instances",
        description = "Updates all Jira instances, fetching all new issues & updated issues of all connected instances",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Update request accepted - Initialization jobs started",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(responseCode = "404", description = "Instance or credentials to update not found"),
        ],
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/update-all")
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
    suspend fun updateAllInstances(): ResponseEntity<List<UpdateJiraInstanceResponse>> {
        val response = updateService.updateAllJiraInstances()
        return ResponseEntity.accepted().body(response)
    }
}
