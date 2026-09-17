package com.sprintstart.sprintstartbackend.connectors.confluence

import com.sprintstart.sprintstartbackend.connectors.overview.models.IConnector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest
class ConfluenceConnectorRegistrationTest {
    @Autowired
    private lateinit var connectors: List<IConnector>

    @Test
    fun `Confluence connector is registered with Spring`() {
        assertThat(connectors.map { connector -> connector.id }).contains("confluence")
    }
}
