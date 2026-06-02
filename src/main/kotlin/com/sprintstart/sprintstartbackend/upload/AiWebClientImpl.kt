package com.sprintstart.sprintstartbackend.upload

import com.sprintstart.sprintstartbackend.WebRequestClient
import com.sprintstart.sprintstartbackend.upload.external.events.AiIngestRequest
import com.sprintstart.sprintstartbackend.upload.external.events.AiIngestResponse
import org.springframework.stereotype.Component
import java.net.URI

@Component("uploadAiWebClient")
class AiWebClientImpl {
    private val client = WebRequestClient()

    /**
     * Sends an artifact to the AI ingestion endpoint for indexing.
     *
     * This function performs a synchronous POST request to the AI service and
     * transmits the uploaded artifact metadata together with its content.
     *
     * @param uri The URI of the AI ingest endpoint.
     * @param body The ingestion request containing the artifact id,
     * filename, and document content.
     * @return The AI ingestion response.
     */
    fun ingest(
        uri: URI,
        body: AiIngestRequest,
    ): AiIngestResponse =
        client.post<
            AiIngestRequest,
            AiIngestResponse,
        >(
            uri,
            body,
        )
}
