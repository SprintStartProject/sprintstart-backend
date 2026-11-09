package com.sprintstart.sprintstartbackend.config

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
internal class OpenApiConfigTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `an endpoint with typed ids keeps its operation id and documents the ids as uuids`() {
        val card = "$.paths['/api/v1/onboarding/me/board/cards/{cardId}']"
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.paths['/api/v1/onboarding/me/board'].get.operationId").value("getMyBoard"))
            .andExpect(jsonPath("$.paths['/api/v1/onboarding/me/board/order'].put.operationId").value("reorder"))
            .andExpect(jsonPath("$card.delete.operationId").value("dismissCard"))
            .andExpect(jsonPath("$card.delete.parameters[?(@.name == 'cardId')].schema.type").value("string"))
            .andExpect(jsonPath("$card.delete.parameters[?(@.name == 'cardId')].schema.format").value("uuid"))
            .andExpect(
                jsonPath(
                    "$.paths['/api/v1/onboarding/me/board'].get.parameters[?(@.name == 'projectId')].schema.format",
                ).value("uuid"),
            )
    }
}
