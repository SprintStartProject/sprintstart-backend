package com.sprintstart.sprintstartbackend.connectors.git.github.repository

import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubOrganization
import org.springframework.data.jpa.repository.JpaRepository

interface GithubOrganizationRepository : JpaRepository<GithubOrganization, String>
