package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.verify
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Base class for blueprint controller tests.
 *
 * Subclasses declare one [EndpointCase] per endpoint of the controller under test. Each case is then exercised
 * through the happy path (expected status, JSON body, service delegation) and through the error paths the endpoint
 * documents in its OpenAPI responses (404, and optionally 400 and 409). Subclasses must implement [resetMocks]
 * to clear their MockK mocks so that every dynamic test starts from a clean stubbing state.
 */
abstract class BlueprintControllerTestSupport {
    protected abstract val controller: Any
    protected abstract val controllerClass: KClass<*>
    protected abstract val endpointCases: List<EndpointCase>

    /**
     * Clears stubs and recorded invocations on the subclass's mocks.
     *
     * Called before every dynamic test so that stubbings from a previous case cannot leak into the next one.
     * Implementations typically delegate to `clearMocks(...)` from MockK.
     */
    protected abstract fun resetMocks()

    @TestFactory
    fun happyPaths(): List<DynamicTest> =
        endpointCases.map { case ->
            dynamicTest("${case.name} returns ${case.expectedStatus} and delegates to the service") {
                resetMocks()
                every { case.serviceCall() } returns (case.response ?: Unit)

                val result =
                    MockMvcBuilders
                        .standaloneSetup(controller)
                        .build()
                        .perform(case.request)
                        .andExpect(status().`is`(case.expectedStatus))
                case.response?.let {
                    result.andExpect(content().json(jacksonObjectMapper().writeValueAsString(it)))
                }
                verify(exactly = 1) { case.serviceCall() }
            }
        }

    @TestFactory
    fun notFound(): List<DynamicTest> =
        endpointCases
            .filter { it.documentsNotFound }
            .map { case ->
                dynamicTest("${case.name} returns 404 when the service rejects the request") {
                    resetMocks()
                    every { case.serviceCall() } throws
                        ResponseStatusException(HttpStatus.NOT_FOUND, "Blueprint resource not found")

                    MockMvcBuilders
                        .standaloneSetup(controller)
                        .build()
                        .perform(case.request)
                        .andExpect(status().isNotFound)
                }
            }

    @TestFactory
    fun badRequest(): List<DynamicTest> =
        endpointCases.filter { it.documentsBadRequest }.map { case ->
            dynamicTest("${case.name} returns 400 when the service rejects the request data") {
                resetMocks()
                every { case.serviceCall() } throws
                    ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request data")

                MockMvcBuilders
                    .standaloneSetup(controller)
                    .build()
                    .perform(case.request)
                    .andExpect(status().isBadRequest)
            }
        }

    @TestFactory
    fun conflict(): List<DynamicTest> =
        endpointCases.filter { it.documentsConflict }.map { case ->
            dynamicTest("${case.name} returns 409 when the blueprint is not editable or the revision is stale") {
                resetMocks()
                every { case.serviceCall() } throws ResponseStatusException(HttpStatus.CONFLICT, "Revision conflict")

                MockMvcBuilders
                    .standaloneSetup(controller)
                    .build()
                    .perform(case.request)
                    .andExpect(status().isConflict)
            }
        }

    @Test
    fun `documents and secures every endpoint`() {
        assertNotNull(controllerClass.java.getAnnotation(RestController::class.java))
        assertNotNull(controllerClass.java.getAnnotation(RequestMapping::class.java))

        val endpointMethods = controllerClass.java.declaredMethods.filter {
            Modifier.isPublic(it.modifiers) && !it.isSynthetic
        }
        assertTrue(endpointMethods.isNotEmpty(), "${controllerClass.simpleName} must expose an endpoint")
        endpointMethods.forEach { method ->
            val endpointName = "${controllerClass.simpleName}.${method.name}"
            assertNotNull(method.getAnnotation(Operation::class.java), "$endpointName must document its API")
            assertNotNull(method.getAnnotation(ApiResponses::class.java), "$endpointName must document its responses")
            assertNotNull(method.getAnnotation(ResponseStatus::class.java), "$endpointName must declare its status")
            assertNotNull(method.getAnnotation(PreAuthorize::class.java), "$endpointName must declare its access rule")
            assertTrue(
                mappingAnnotations.any { method.getAnnotation(it.java) != null },
                "$endpointName must declare an HTTP mapping",
            )
        }
    }

    /**
     * Describes a single controller endpoint for the generated tests.
     *
     * @property name Human-readable endpoint identifier used in dynamic test names, e.g.
     *      `POST /phases/{id}/requirements`.
     * @property request The MockMvc request to perform, including path variables and, where required, a JSON body.
     * @property expectedStatus The HTTP status declared via `@ResponseStatus` on the controller method.
     * @property response The service response to stub; `null` when the endpoint returns no body (e.g. 204).
     * @property serviceCall The mocked service call the endpoint delegates to.
     * @property documentsNotFound Whether the endpoint documents a 404 response in its `@ApiResponses`.
     * @property documentsBadRequest Whether the endpoint documents a 400 response in its `@ApiResponses`.
     * @property documentsConflict Whether the endpoint documents a 409 response in its `@ApiResponses`.
     */
    data class EndpointCase(
        val name: String,
        val request: MockHttpServletRequestBuilder,
        val expectedStatus: Int,
        val response: Any?,
        val serviceCall: () -> Any,
        val documentsNotFound: Boolean = true,
        val documentsBadRequest: Boolean = false,
        val documentsConflict: Boolean = false,
    )

    private companion object {
        val mappingAnnotations =
            listOf(
                GetMapping::class,
                PostMapping::class,
                PutMapping::class,
                PatchMapping::class,
                DeleteMapping::class,
            )
    }
}
