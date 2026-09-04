package com.sprintstart.sprintstartbackend.connectors.confluence.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
internal class ConfluenceOpenApiTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `OpenAPI exposes Confluence operations without credential response properties`() {
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(
                jsonPath("$.paths['/api/v1/confluence/projects/{projectId}/connections'].post").exists(),
            ).andExpect(
                jsonPath("$.paths['/api/v1/confluence/projects/{projectId}/connections/{connectionId}/update'].post")
                    .exists(),
            ).andExpect(
                jsonPath("$.components.schemas.ConfluenceConnectionResponse.properties.apiToken").doesNotExist(),
            ).andExpect(
                jsonPath("$.components.schemas.ConfluenceConnectionResponse.properties.email").doesNotExist(),
            ).andExpect(
                jsonPath("$.components.schemas.CreateConfluenceConnectionRequest.properties.apiToken.writeOnly")
                    .value(true),
            )
    }
}
