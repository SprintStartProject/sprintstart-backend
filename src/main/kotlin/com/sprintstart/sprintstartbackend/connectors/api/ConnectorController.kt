package com.sprintstart.sprintstartbackend.connectors.api

import com.sprintstart.sprintstartbackend.connectors.core.models.IConnector
import com.sprintstart.sprintstartbackend.connectors.core.models.api.request.ConfigureConnectorRequest
import com.sprintstart.sprintstartbackend.connectors.core.models.api.request.PatchSourcesRequest
import com.sprintstart.sprintstartbackend.connectors.core.models.api.response.ConfigureConnectorResponse
import com.sprintstart.sprintstartbackend.connectors.core.models.api.response.ConnectorConfigurationDto
import com.sprintstart.sprintstartbackend.connectors.core.models.api.response.GetSourcesOfConnectorResponse
import com.sprintstart.sprintstartbackend.connectors.core.models.api.response.toDto
import com.sprintstart.sprintstartbackend.connectors.core.service.ConnectorConfigurationService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/connectors")
class ConnectorController(
    private val connectors: List<IConnector>,
    private val connectorConfigurationService: ConnectorConfigurationService,
) {
    // Verify all connector ids are truly unique on bean init
    init {
        val duplicates = connectors.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate connector IDs detected: $duplicates" }
    }

    @GetMapping
    fun listAll(): ResponseEntity<List<ConnectorConfigurationDto>> =
        ResponseEntity.ok(connectors.map { it.toDto() })

    @PatchMapping("/{id}")
    fun configureConnector(
        @PathVariable id: String,
        @Valid @RequestBody request: ConfigureConnectorRequest,
    ): ResponseEntity<ConfigureConnectorResponse> =
        ResponseEntity.ok(connectorConfigurationService.configure(id, request))

    @GetMapping("/{id}/sources")
    fun getSourcesOfConnector(@PathVariable id: String): ResponseEntity<GetSourcesOfConnectorResponse> =
        ResponseEntity.ok(connectorConfigurationService.getSourcesOfConnector(id))

    @PatchMapping("/{id}/sources/status")
    fun patchSourcesOfConnector(
        @PathVariable id: String,
        @Valid @RequestBody request: PatchSourcesRequest,
    ): ResponseEntity<Unit> {
        connectorConfigurationService.patchSourcesOfConnector(id)
        return ResponseEntity.noContent().build()
    }
}
