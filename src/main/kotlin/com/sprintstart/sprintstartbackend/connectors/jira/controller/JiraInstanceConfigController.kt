package com.sprintstart.sprintstartbackend.connectors.jira.controller

import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.config.ConfigureAllJiraInstancesRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.config.ConfigureJiraInstanceRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.config.GetJiraInstanceConfigResponse
import com.sprintstart.sprintstartbackend.connectors.jira.service.JiraInstanceConfigService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/jira/config")
internal class JiraInstanceConfigController(
    private val configService: JiraInstanceConfigService,
) {
    /**
     * Configures all Jira instances with the settings provided in the request.
     * Updates the schedule specification and auto-update flag uniformly across all configured Jira instances.
     *
     * @param request An object of [ConfigureAllJiraInstancesRequest] containing the schedule specification
     * and auto-update settings to be applied to all Jira instances.
     * @return A [ResponseEntity] with no content indicating the operation was performed successfully.
     */
    @Operation(
        summary = "Configure all Jira instances uniformly",
        description = "Updates the configuration of all Jira instances with the provided settings.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Configurations were successfully updated.",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('PM')")
    fun configureAll(
        @Valid @RequestBody request: ConfigureAllJiraInstancesRequest,
    ): ResponseEntity<Unit> {
        configService.configureAll(request)
        return ResponseEntity.noContent().build()
    }

    /**
     * Retrieves the configurations of all Jira instances.
     *
     * @return A [ResponseEntity] containing a list of [GetJiraInstanceConfigResponse] representing
     * the configurations of all Jira instances.
     */
    @Operation(
        summary = "Retrieve all Jira instance configurations",
        description = "Retrieves the configuration of all Jira instances.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Retrieved all Jira instance configurations successfully.",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('PM')")
    fun getAll(): ResponseEntity<List<GetJiraInstanceConfigResponse>> {
        val response = configService.getAll()
        return ResponseEntity.ok(response)
    }

    /**
     * Configures a specific Jira instance with the provided settings.
     *
     * @param request An object of [ConfigureJiraInstanceRequest] containing the configuration details
     * to be applied to the specified Jira instance.
     * @return A [ResponseEntity] with no content indicating the configuration was successfully updated.
     */
    @Operation(
        summary = "Configures a specific Jira instance",
        description = "Updates the configuration of a specific Jira instance with the provided settings.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Configuration was successfully updated.",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(responseCode = "404", description = "Configuration to update not found"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/configure")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PM')")
    fun configureInstance(
        @Valid @RequestBody request: ConfigureJiraInstanceRequest,
    ): ResponseEntity<Unit> {
        configService.configureInstance(request)
        return ResponseEntity.noContent().build()
    }

    /**
     * Retrieves the configuration of a specific Jira instance based on its ID.
     *
     * @param instanceId The unique identifier of the Jira instance whose configuration is to be retrieved.
     * @return A [ResponseEntity] containing a [GetJiraInstanceConfigResponse] with the configuration of the
     *         specified Jira instance.
     */
    @Operation(
        summary = "Retrieves the configuration of a specific Jira instance",
        description = "Retrieves the configuration of a specific Jira instance by its ID.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Retrieved Jira instance configuration successfully.",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(responseCode = "404", description = "Configuration to retrieve not found"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{instanceId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PM')")
    fun getConfigOfInstance(
        @PathVariable instanceId: String,
    ): ResponseEntity<GetJiraInstanceConfigResponse> {
        val result = configService.getConfigOfInstance(instanceId)
        return ResponseEntity.ok(result)
    }
}
