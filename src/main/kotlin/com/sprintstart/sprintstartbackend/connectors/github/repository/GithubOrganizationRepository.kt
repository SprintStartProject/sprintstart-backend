package com.sprintstart.sprintstartbackend.connectors.github.repository

import com.sprintstart.sprintstartbackend.connectors.github.models.GithubOrganization
import org.springframework.data.jpa.repository.JpaRepository

interface GithubOrganizationRepository : JpaRepository<GithubOrganization, String>
