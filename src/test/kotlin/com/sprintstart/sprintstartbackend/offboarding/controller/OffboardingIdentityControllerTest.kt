package com.sprintstart.sprintstartbackend.offboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.offboarding.model.exception.IdentityNotLinkedException
import com.sprintstart.sprintstartbackend.offboarding.scoring.AliasLink
import com.sprintstart.sprintstartbackend.offboarding.scoring.MemberMatch
import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.offboarding.scoring.SeenIdentity
import com.sprintstart.sprintstartbackend.offboarding.scoring.SourceId
import com.sprintstart.sprintstartbackend.offboarding.service.IdentityLinkService
import com.sprintstart.sprintstartbackend.offboarding.service.MemberIdentityService
import com.sprintstart.sprintstartbackend.offboarding.service.SeenIdentityService
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.security.ProjectAuthorization
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [OffboardingIdentityController::class])
@AutoConfigureMockMvc
@Import(SecurityConfig::class)
class OffboardingIdentityControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var linkService: IdentityLinkService

    @MockkBean
    private lateinit var seenService: SeenIdentityService

    @MockkBean
    private lateinit var memberService: MemberIdentityService

    @MockkBean(name = "projectAuth")
    private lateinit var projectAuthorization: ProjectAuthorization

    private val projectId = UUID.randomUUID()
    private val base = "/api/v1/projects/$projectId/offboarding/identities"
    private val pmJwt = jwt().jwt { it.subject("pm-id") }.authorities(SimpleGrantedAuthority("ROLE_PM"))
    private val userJwt = jwt().jwt { it.subject("user-id") }.authorities(SimpleGrantedAuthority("ROLE_USER"))
    private val link = AliasLink(PersonKey("ada@x.org"), PersonKey("ada"))

    private fun allowManager() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns true
    }

    @Test
    fun `a manager sees the links and every identity the evidence names`() {
        allowManager()
        every { linkService.links(ProjectId(projectId)) } returns listOf(link)
        every { seenService.seen(ProjectId(projectId), null) } returns listOf(
            SeenIdentity(
                identity = PersonKey("ada@x.org"),
                person = PersonKey("ada"),
                touches = mapOf(SourceId.REVIEWS to 1, SourceId.COMMITS to 4),
                lastSeenAt = Instant.parse("2026-03-01T00:00:00Z"),
            ),
        )

        mockMvc
            .perform(get(base).with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.links[0].alias").value("ada@x.org"))
            .andExpect(jsonPath("$.links[0].person").value("ada"))
            .andExpect(jsonPath("$.seen[0].identity").value("ada@x.org"))
            .andExpect(jsonPath("$.seen[0].person").value("ada"))
            .andExpect(jsonPath("$.seen[0].touches.commits").value(4))
            .andExpect(jsonPath("$.seen[0].touches.reviews").value(1))
            .andExpect(jsonPath("$.seen[0].lastSeenAt").value("2026-03-01T00:00:00Z"))
    }

    @Test
    fun `a manager sees which members the evidence is attributed to`() {
        allowManager()
        val ada = UUID.randomUUID()
        val ben = UUID.randomUUID()
        every { memberService.matches(ProjectId(projectId), null) } returns listOf(
            MemberMatch(
                member = UserId(ada),
                displayName = "Ada",
                githubLogin = "ada",
                identities = listOf(
                    SeenIdentity(
                        identity = PersonKey("ada@x.org"),
                        person = PersonKey("ada@x.org"),
                        touches = mapOf(SourceId.COMMITS to 4),
                        lastSeenAt = Instant.parse("2026-03-01T00:00:00Z"),
                    ),
                ),
                suggestions = listOf(AliasLink(PersonKey("ada@x.org"), PersonKey("ada"))),
            ),
            MemberMatch(UserId(ben), "Ben", null, emptyList(), emptyList()),
        )

        mockMvc
            .perform(get("$base/members").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.matched").value(1))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.members[0].member").value(ada.toString()))
            .andExpect(jsonPath("$.members[0].displayName").value("Ada"))
            .andExpect(jsonPath("$.members[0].matched").value(true))
            .andExpect(jsonPath("$.members[0].identities[0].identity").value("ada@x.org"))
            .andExpect(jsonPath("$.members[0].suggestions[0].alias").value("ada@x.org"))
            .andExpect(jsonPath("$.members[0].suggestions[0].person").value("ada"))
            .andExpect(jsonPath("$.members[1].matched").value(false))
            .andExpect(jsonPath("$.members[1].identities.length()").value(0))
    }

    @Test
    fun `a date is passed on to the member matching`() {
        allowManager()
        val moment = Instant.parse("2026-05-01T00:00:00Z")
        every { memberService.matches(ProjectId(projectId), moment) } returns emptyList()

        mockMvc
            .perform(get("$base/members").param("asOf", "2026-05-01T00:00:00Z").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))
    }

    @Test
    fun `a date is passed on to the listing`() {
        allowManager()
        val moment = Instant.parse("2026-05-01T00:00:00Z")
        every { linkService.links(ProjectId(projectId)) } returns emptyList()
        every { seenService.seen(ProjectId(projectId), moment) } returns emptyList()

        mockMvc
            .perform(get(base).param("asOf", "2026-05-01T00:00:00Z").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.seen.length()").value(0))
    }

    @Test
    fun `a manager links two identities and gets the links back`() {
        allowManager()
        every { linkService.link(ProjectId(projectId), PersonKey("ada@x.org"), PersonKey("ada")) } returns listOf(link)

        mockMvc
            .perform(
                put("$base/links")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"alias": "Ada@X.org", "person": "ada"}""")
                    .with(pmJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].alias").value("ada@x.org"))
            .andExpect(jsonPath("$[0].person").value("ada"))
    }

    @Test
    fun `a blank identity is a bad request and links nothing`() {
        allowManager()

        mockMvc
            .perform(
                put("$base/links")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"alias": " ", "person": "ada"}""")
                    .with(pmJwt),
            ).andExpect(status().isBadRequest)

        verify(exactly = 0) { linkService.link(any(), any(), any()) }
    }

    @Test
    fun `unlinking an identity answers no content`() {
        allowManager()
        every { linkService.unlink(ProjectId(projectId), PersonKey("ada@x.org")) } just runs

        mockMvc
            .perform(delete("$base/links").param("alias", "Ada@x.org").with(pmJwt))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `unlinking an identity that is not linked is not found`() {
        allowManager()
        every { linkService.unlink(ProjectId(projectId), PersonKey("nobody")) } throws
            IdentityNotLinkedException("nobody")

        mockMvc
            .perform(delete("$base/links").param("alias", "nobody").with(pmJwt))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `unlinking a blank identity is a bad request`() {
        allowManager()

        mockMvc
            .perform(delete("$base/links").param("alias", " ").with(pmJwt))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `someone who does not manage the project is refused everywhere`() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns false

        mockMvc.perform(get(base).with(pmJwt)).andExpect(status().isForbidden)
        mockMvc.perform(get("$base/members").with(pmJwt)).andExpect(status().isForbidden)
        mockMvc
            .perform(
                put("$base/links")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"alias": "a", "person": "b"}""")
                    .with(pmJwt),
            ).andExpect(status().isForbidden)
        mockMvc.perform(delete("$base/links").param("alias", "a").with(pmJwt)).andExpect(status().isForbidden)

        verify(exactly = 0) { linkService.link(any(), any(), any()) }
        verify(exactly = 0) { linkService.unlink(any(), any()) }
    }

    @Test
    fun `a user without the manager role is refused`() {
        allowManager()

        mockMvc.perform(get(base).with(userJwt)).andExpect(status().isForbidden)
    }

    @Test
    fun `an anonymous request is refused`() {
        mockMvc.perform(get(base)).andExpect(status().isUnauthorized)
    }
}
