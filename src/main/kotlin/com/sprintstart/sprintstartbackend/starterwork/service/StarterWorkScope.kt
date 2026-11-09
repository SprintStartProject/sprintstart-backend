package com.sprintstart.sprintstartbackend.starterwork.service

import com.sprintstart.sprintstartbackend.connectors.git.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.shared.ids.SourceId
import com.sprintstart.sprintstartbackend.starterwork.external.model.GITHUB_SOURCE_PREFIX
import com.sprintstart.sprintstartbackend.starterwork.external.model.GithubRepositoryName
import com.sprintstart.sprintstartbackend.starterwork.external.model.githubRepositoryOf
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Which starter work belongs to a project, for team mode.
 *
 * The pool has no project. A task is an issue in a repository, and a repository can be linked to
 * several projects, so a task belongs to every project its repository is linked to — and to none
 * when it is not a GitHub issue or its repository is linked to no project. Those stay with an admin on
 * the starter-work page.
 *
 * Every starter-work tool asks this, the lists and the actions alike, so what a manager is shown and
 * what they may change cannot disagree.
 *
 * Read-only transactions, because a repository's linked projects are a lazy collection.
 */
@Component
class StarterWorkScope(
    private val githubRepositoryApi: GithubRepositoryApi,
) {
    /** Whether the task or issue [sourceId] names belongs to [projectId]. */
    @Transactional(readOnly = true)
    fun covers(sourceId: SourceId, projectId: UUID): Boolean {
        val repository = githubRepositoryOf(sourceId) ?: return false
        return projectId in projectsOf(repository)
    }

    /** The [items] whose source belongs to [projectId], looking each repository up once. */
    @Transactional(readOnly = true)
    fun <T> onProject(items: List<T>, projectId: UUID, sourceIdOf: (T) -> String): List<T> {
        val covered = mutableMapOf<GithubRepositoryName, Boolean>()
        return items.filter { item ->
            val repository = githubRepositoryOf(SourceId(sourceIdOf(item))) ?: return@filter false
            covered.getOrPut(repository) { projectId in projectsOf(repository) }
        }
    }

    /**
     * The [items] a hire on [projectId] may be given: the shared pool, plus this project's own work.
     *
     * Wider than [onProject] in one way only. A hand-authored task (`authored:`) or a Jira issue has
     * no repository to scope it by and never had one, so it stays in the shared pool an admin
     * curates, as it always has.
     *
     * Every GitHub source is scoped exactly as [onProject] scopes it, and so fails closed: a task
     * whose repository is malformed, unknown, disconnected mid-read, or linked to no project reaches
     * no hire at all. It must, because a repository can lose its projects after a manager promoted
     * one of its issues — falling back to the shared pool there would hand that issue to hires on
     * unrelated projects, which is the leak this scope exists to close.
     */
    @Transactional(readOnly = true)
    fun <T> forHiresOn(items: List<T>, projectId: UUID, sourceIdOf: (T) -> String): List<T> {
        val visible = mutableMapOf<GithubRepositoryName, Boolean>()
        return items.filter { item ->
            val sourceId = sourceIdOf(item)
            val repository = githubRepositoryOf(SourceId(sourceId))
                ?: return@filter !sourceId.startsWith(GITHUB_SOURCE_PREFIX)
            visible.getOrPut(repository) { projectId in projectsOf(repository) }
        }
    }

    private fun projectsOf(repository: GithubRepositoryName): Set<UUID> {
        val id = githubRepositoryApi.getRepositoryIdByOwnerAndName(repository.owner, repository.name)
            ?: return emptySet()
        return try {
            githubRepositoryApi.getRepositoryProjectIdsById(id)
        } catch (_: NoSuchElementException) {
            // Disconnected between the two reads.
            emptySet()
        }
    }
}
