package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.CommitFileChangesCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ChangeAuthor
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FileChange
import com.sprintstart.sprintstartbackend.ingestion.model.entity.LineCounts
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.EvidenceArtifactNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.FileChangeRepository
import com.sprintstart.sprintstartbackend.shared.git.CommitFile
import com.sprintstart.sprintstartbackend.shared.git.CommitFiles
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Records which files each stored commit changed.
 *
 * The record is a projection of the commit's history, so recording the same commit again replaces
 * what was stored instead of adding to it. That is what makes a full re-read of a repository's
 * history safe to repeat.
 */
@Service
class CommitFileChangeService(
    private val artifactRepository: ArtifactRepository,
    private val fileChangeRepository: FileChangeRepository,
) {
    /**
     * Replaces the changed files stored for a commit.
     *
     * A commit that changed too many files to list is stored with no rows at all: its files carry no
     * signal about who knows what, and a bulk reformat would otherwise make its author look like the
     * owner of everything it touched.
     *
     * @param command The commit and the files it changed.
     * @throws EvidenceArtifactNotFoundException when the commit artifact is not stored.
     */
    @Transactional
    fun record(command: CommitFileChangesCommand) {
        val artifact = artifactRepository.findBySourceId(command.sourceId)
            ?: throw EvidenceArtifactNotFoundException(command.sourceId)

        fileChangeRepository.deleteAllByArtifactId(artifact.id)

        when (val files = command.files) {
            is CommitFiles.Listed -> fileChangeRepository.saveAll(files.files.map { toEntity(artifact, command, it) })
            is CommitFiles.TooMany -> Unit
        }
    }

    private fun toEntity(artifact: Artifact, command: CommitFileChangesCommand, file: CommitFile): FileChange =
        FileChange(
            artifact = artifact,
            repository = command.repository,
            path = file.path,
            renamedFrom = file.renamedFrom,
            author = ChangeAuthor(name = command.authorName, email = command.authorEmail),
            changedAt = command.changedAt,
            lines = LineCounts(added = file.linesAdded, removed = file.linesRemoved),
        )
}
