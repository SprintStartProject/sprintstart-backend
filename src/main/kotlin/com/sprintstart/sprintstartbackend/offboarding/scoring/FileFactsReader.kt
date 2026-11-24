package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileAuthorshipReading
import org.springframework.stereotype.Component

/** Reads what ingestion measured of the files of a project: how complicated they are and who wrote their lines. */
@Component
class FileFactsReader(
    private val api: ContributionEvidenceApi,
) {
    /**
     * The facts about the files of [repositories].
     *
     * The authors of lines are counted as the people the confirmed [identities] say they are.
     */
    fun of(repositories: Collection<String>, identities: Identities): FileFacts =
        FileFacts(
            complexities = complexities(repositories),
            authorship = authorship(repositories, identities),
        )

    private fun complexities(repositories: Collection<String>): Map<FileKey, Double> =
        api.fileComplexities(repositories).associate { FileKey(it.repository, it.path) to it.indentation }

    private fun authorship(
        repositories: Collection<String>,
        identities: Identities,
    ): Map<FileKey, Map<PersonKey, Int>> =
        api
            .fileAuthorship(repositories)
            .groupBy { FileKey(it.repository, it.path) }
            .mapValues { (_, rows) -> linesByPerson(rows, identities) }

    private fun linesByPerson(rows: List<FileAuthorshipReading>, identities: Identities): Map<PersonKey, Int> =
        rows
            .groupBy({ identities.resolve(PersonKey.ofEmail(it.authorEmail)) }, { it.lines })
            .mapValues { it.value.sum() }
}
