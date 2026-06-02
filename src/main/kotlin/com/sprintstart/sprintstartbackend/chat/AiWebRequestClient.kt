package com.sprintstart.sprintstartbackend.chat

import com.sprintstart.sprintstartbackend.WebRequestClient
import com.sprintstart.sprintstartbackend.chat.models.exceptions.AiResponseException
import com.sprintstart.sprintstartbackend.chat.models.requests.AiGenerateChatTitleRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.AiPromptRequest
import com.sprintstart.sprintstartbackend.chat.models.responses.AiGenerateChatTitleResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.AiStreamMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Component
class AiWebClientImpl {
    private val client = AiWebRequestClient()

    val jsonParser = client.jsonParser

    fun getChatTitle(uri: URI, prompt: String): AiGenerateChatTitleResponse =
        client.post<AiGenerateChatTitleRequest, AiGenerateChatTitleResponse>(uri, AiGenerateChatTitleRequest(prompt))

    fun streamPrompt(uri: URI, body: AiPromptRequest): Flow<String> =
        client.streamPost<AiPromptRequest>(uri, body)
}

class AiWebRequestClient : WebRequestClient() {
    /**
     * Opens up a new SSE stream with a POST request.
     *
     * This function calls the given `uri` and expects the receiver to open up a new SSE stream.
     * In order to initiate a new SSE stream, we hit the receiver with a POST request, containing the given
     * `payload`.
     *
     * @param uri The uri to call.
     * @param body The payload to transmit initially.
     * @return [Flow<String>] A flow of strings.
     * @see Flow
     */
    inline fun <reified PayloadType> streamPost(
        uri: URI,
        body: PayloadType,
    ): Flow<String> {
        val jsonPayload = WebRequestClient().jsonParser.encodeToString(body)
        return streamRequestToAi(uri, "POST", jsonPayload)
    }

    /**
     * Opens up a new SSE stream.
     *
     * This function hits the receiver with a request of the given type, containing the given payload,
     * and expects the caller to start an SSE stream of strings.
     *
     * @param uri The uri to call.
     * @param method The http method to use.
     * @param payload The payload to transmit initially.
     * @return [Flow<String>] The flow of data.
     * @see [Http method docs](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Methods)
     * @see Flow
     */
    @PublishedApi
    internal fun streamRequestToAi(
        uri: URI,
        method: String,
        payload: String,
    ): Flow<String> = flow {
        val client = HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build()

        val request = HttpRequest
            .newBuilder()
            .uri(uri)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream") // Tell AI we want a stream
            .method(method.uppercase(), HttpRequest.BodyPublishers.ofString(payload))
            .build()

        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).await()

        response.body().use { stream ->
            val iterator = stream.iterator()

            while (iterator.hasNext()) {
                val line = iterator.next()

                if (line.startsWith("data:")) {
                    val rawJson = line.removePrefix("data:").trim()

                    try {
                        val message = WebRequestClient().jsonParser.decodeFromString<AiStreamMessage>(rawJson)

                        when (message.type) {
                            "done", "" -> break
                            "token", "citation" -> emit(rawJson)
                            "error" -> throw AiResponseException("Ai responded with error: ${message.content}")
                        }
                    } catch (e: AiResponseException) {
                        System.err.println("Error parsing chunk incoming from ai: ${e.message}")
                    }
                }
            }
        }
    }
}
