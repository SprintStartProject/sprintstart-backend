package com.sprintstart.sprintstartbackend.connectors.bitbucket.service.internal

import com.sprintstart.sprintstartbackend.connectors.bitbucket.BitbucketClient
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class BitbucketFileService(
    private val bitbucketClient: BitbucketClient,
) {
    @Tracked("Fetching and ingesting files of Bitbucket repository")
    fun fetchAndIngestFilesOfRepository(repositoryId: UUID, transactionId: UUID) {
    }
}
