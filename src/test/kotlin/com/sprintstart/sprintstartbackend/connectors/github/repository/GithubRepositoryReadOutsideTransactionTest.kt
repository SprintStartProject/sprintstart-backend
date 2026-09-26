package com.sprintstart.sprintstartbackend.connectors.github.repository

import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositorySnapshot
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUser
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * `GithubUpdatesService.updateRepository` is not transactional, and reads a connection's snapshot and
 * token after loading it. Started from a REST request that works because the request holds a session;
 * started from the buddy's confirm there may be none. This loads a connection the way that call does —
 * with no transaction and no session — and checks that what a sync reads is there.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import(CryptoConfiguration::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GithubRepositoryReadOutsideTransactionTest {
    @Autowired
    private lateinit var repository: GithubRepositoryConnectionRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `a connection loaded with no session still has its snapshot and its token`() {
        val name = "repo-${UUID.randomUUID()}"
        TransactionTemplate(transactionManager).executeWithoutResult {
            val user = GithubUser(id = GithubUserPat("auth|pm", "work-$name"), token = "secret-$name")
            entityManager.persist(user)
            val connection = GithubRepositoryConnection(owner = "acme", name = name, user = user)
            connection.snapshot = GithubRepositorySnapshot(repository = connection)
            entityManager.persist(connection)
        }

        val loaded = repository.findByOwnerAndName("acme", name)!!

        assertThat(loaded.snapshot).isNotNull
        assertThat(loaded.user.token).isEqualTo("secret-$name")
    }
}
