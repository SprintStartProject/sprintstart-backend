package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.offboarding.model.exception.IdentityNotLinkedException
import com.sprintstart.sprintstartbackend.offboarding.scoring.AliasLink
import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/** The links are rows with a unique key per project, so the rules about them meet a real database. */
@ActiveProfiles("test")
@DataJpaTest
@Import(CryptoConfiguration::class, IdentityLinkService::class)
class IdentityLinkServiceTest {
    @Autowired
    private lateinit var service: IdentityLinkService

    private val project = ProjectId(UUID.randomUUID())
    private val otherProject = ProjectId(UUID.randomUUID())
    private val ada = PersonKey("ada")
    private val adaEmail = PersonKey("ada@x.org")
    private val adaWork = PersonKey("ada@work.org")

    @Test
    fun `a project without links has none`() {
        assertThat(service.links(project)).isEmpty()
        assertThat(service.identities(project).resolve(adaEmail)).isEqualTo(adaEmail)
    }

    @Test
    fun `linking folds the first identity into the second`() {
        val links = service.link(project, adaEmail, ada)

        assertThat(links).containsExactly(AliasLink(adaEmail, ada))
        assertThat(service.identities(project).resolve(adaEmail)).isEqualTo(ada)
    }

    @Test
    fun `linking identities that already count as one person changes nothing`() {
        service.link(project, adaEmail, ada)

        val again = service.link(project, adaEmail, ada)
        val reversed = service.link(project, ada, adaEmail)

        assertThat(again).containsExactly(AliasLink(adaEmail, ada))
        assertThat(reversed).containsExactly(AliasLink(adaEmail, ada))
    }

    @Test
    fun `an identity linked to an alias joins the person behind it instead of chaining`() {
        service.link(project, adaEmail, ada)

        val links = service.link(project, adaWork, adaEmail)

        assertThat(links).containsExactlyInAnyOrder(AliasLink(adaEmail, ada), AliasLink(adaWork, ada))
    }

    @Test
    fun `linking two people merges them and everything that pointed at either`() {
        service.link(project, adaEmail, ada)
        val ben = PersonKey("ben")
        val benEmail = PersonKey("ben@x.org")
        service.link(project, benEmail, ben)

        val links = service.link(project, ada, ben)

        assertThat(links).containsExactlyInAnyOrder(
            AliasLink(adaEmail, ben),
            AliasLink(benEmail, ben),
            AliasLink(ada, ben),
        )
    }

    @Test
    fun `links belong to one project`() {
        service.link(project, adaEmail, ada)

        assertThat(service.links(otherProject)).isEmpty()
        service.link(otherProject, adaEmail, PersonKey("someone-else"))
        assertThat(service.identities(project).resolve(adaEmail)).isEqualTo(ada)
    }

    @Test
    fun `links are listed in alias order`() {
        service.link(project, PersonKey("zed@x.org"), ada)
        service.link(project, adaEmail, ada)

        assertThat(service.links(project).map { it.alias.value }).containsExactly("ada@x.org", "zed@x.org")
    }

    @Test
    fun `unlinking takes back one link`() {
        service.link(project, adaEmail, ada)
        service.link(project, adaWork, ada)

        service.unlink(project, adaEmail)

        assertThat(service.links(project)).containsExactly(AliasLink(adaWork, ada))
    }

    @Test
    fun `unlinking what is not linked is refused, also in another project`() {
        service.link(project, adaWork, ada)

        assertThrows<IdentityNotLinkedException> { service.unlink(project, adaEmail) }
        assertThrows<IdentityNotLinkedException> { service.unlink(otherProject, adaWork) }
        assertThat(service.links(project)).containsExactly(AliasLink(adaWork, ada))
    }
}
