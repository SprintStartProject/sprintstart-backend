package com.sprintstart.sprintstartbackend.connectors.overview.controller

import com.sprintstart.sprintstartbackend.connectors.overview.models.IConnector
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.request.ConfigureConnectorRequest
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.request.PatchSourcesRequest
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.response.ConfigureConnectorResponse
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.response.ConnectorDto
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.response.GetSourcesOfConnectorResponse
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.response.PatchSourcesOfConnectorResponse
import com.sprintstart.sprintstartbackend.connectors.overview.service.ConnectorConfigurationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// The pattern to match connector ids. Matches all strings,
// that are lowercase, don't contain spaces, `-` is allowed.
private const val ID_PATTERN = "^[a-z0-9-]+$"

@Validated
@RestController
@RequestMapping("/api/v1/connectors")
@Tag(name = "Connector Overview", description = "An overview over all available connectors and their sources.")
class ConnectorController(
    connectors: List<IConnector>,
    private val connectorConfigurationService: ConnectorConfigurationService,
) {
    // Verify all connector ids are truly unique on bean init
    init {
        val duplicates = connectors.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate connector IDs detected: $duplicates" }
    }

    /**
     * Retrieves a list of all connectors that are available.
     *
     * Available in this context means configurable. Whether a connector is already configured, enabled or disabled,
     * they're all retrieved here.
     *
     * @return description
     */
    @Operation(
        summary = "List all connectors",
        description = "Retrieve a list of all available connectors - enabled or not",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved all connectors",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(
                responseCode = "404",
                description = "The in-memory state is desynced with the db state - should not happen",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @GetMapping
    fun listAll(): ResponseEntity<List<ConnectorDto>> =
        ResponseEntity.ok(connectorConfigurationService.findAllConnectors())

    /**
     * Enables or disables a connector globally for the whole application.
     *
     * @param id The id of the connector to configure
     * @param request Contains the flag: `enabled` to update the connector status.
     * @return The updated connector.
     */
    @Operation(
        summary = "Configure connector",
        description = "Allows configuring a given connector",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully configured connector",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(
                responseCode = "404",
                description = "The connector to configure was not found",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @PatchMapping("/{id}")
    suspend fun configureConnector(
        @Pattern(regexp = ID_PATTERN) @PathVariable id: String,
        @Valid @RequestBody request: ConfigureConnectorRequest,
    ): ResponseEntity<ConfigureConnectorResponse> =
        ResponseEntity.ok(connectorConfigurationService.configure(id, request))

    /**
     * Retrieves all configured sources of a given connector.
     *
     * @param id The id of the connector to fetch sources for.
     * @return a list of all configured sources of the given connector.
     */
    @Operation(
        summary = "Get sources of connector",
        description = "Retrieve all available sources defined on a given connector",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved sources",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(
                responseCode = "404",
                description = "The connector to retrieve sources of was not found",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @GetMapping("/{id}/sources")
    fun getSourcesOfConnector(
        @Pattern(regexp = ID_PATTERN) @PathVariable id: String,
    ): ResponseEntity<GetSourcesOfConnectorResponse> =
        ResponseEntity.ok(connectorConfigurationService.getSourcesOfConnector(id))

    /**
     * Patches a given list of sources of a given connector, changing their statuses (`enabled`/`disabled`).
     *
     * @param id The id of the connector to patch sources for.
     * @param request contains the information about which sources to patch and how.
     * @return A list of the updated sources.
     */
    @Operation(
        summary = "Patch sources of connector",
        description = "Patches a given list of sources of a connector, eg. changes their status",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully patched sources",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(
                responseCode = "404",
                description = "The connector to patch sources of was not found",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @PatchMapping("/{id}/sources/status")
    suspend fun patchSourcesOfConnector(
        @Pattern(regexp = ID_PATTERN) @PathVariable id: String,
        @Valid @RequestBody request: PatchSourcesRequest,
    ): ResponseEntity<PatchSourcesOfConnectorResponse> {
        return ResponseEntity.ok(connectorConfigurationService.patchSourcesIfConnectorExists(id, request))
    }
}
