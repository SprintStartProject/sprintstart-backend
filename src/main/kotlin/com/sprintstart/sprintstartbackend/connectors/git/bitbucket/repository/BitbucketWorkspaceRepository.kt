package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketWorkspace
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
internal interface BitbucketWorkspaceRepository : JpaRepository<BitbucketWorkspace, String>
