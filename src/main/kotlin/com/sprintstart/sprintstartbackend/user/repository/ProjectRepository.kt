package com.sprintstart.sprintstartbackend.user.repository

import com.sprintstart.sprintstartbackend.user.model.entity.Project
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface ProjectRepository : JpaRepository<Project, UUID> {
    fun findByName(name: String): Project?

    /**
     * Returns the authentication identifier of a project's manager.
     *
     * Deliberately projects only the `authId` so authorization checks do not load the manager
     * entity graph. Returns an empty [Optional] both when the project does not exist and when it
     * has no manager assigned.
     *
     * @param projectId Project identifier.
     * @return The manager's `authId`, if the project exists and has a manager.
     */
    @Query("select m.authId from Project p join p.manager m where p.id = :projectId")
    fun findManagerAuthId(projectId: UUID): Optional<String>

    /**
     * Returns all projects managed by the user with the given authentication identifier.
     *
     * @param authId The manager's external authentication identifier.
     * @return All projects the user is the assigned manager of.
     */
    @Query("select p from Project p where p.manager.authId = :authId")
    fun findAllByManagerAuthId(authId: String): List<Project>

    /**
     * Returns all projects with their manager eagerly fetched.
     *
     * Used instead of [findAll] wherever the manager is mapped into a response, because the lazy
     * `manager` association would otherwise trigger one additional select per project.
     *
     * @return All projects, each with its manager loaded.
     */
    @Query("select distinct p from Project p left join fetch p.manager")
    fun findAllWithManager(): List<Project>

    /**
     * Clears the manager assignment of every project managed by the given user.
     *
     * Must be called before a user row is deleted: the manager foreign key cannot express
     * `ON DELETE SET NULL` through `@ManyToOne`, so deleting a manager would otherwise fail with a
     * constraint violation.
     *
     * @param userId Identifier of the user being removed as manager.
     * @return The number of projects whose manager was cleared.
     */
    @Modifying
    @Query("update Project p set p.manager = null where p.manager.id = :userId")
    fun clearManagerForUser(userId: UUID): Int
}
