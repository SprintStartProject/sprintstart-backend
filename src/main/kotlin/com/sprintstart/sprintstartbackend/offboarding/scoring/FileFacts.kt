package com.sprintstart.sprintstartbackend.offboarding.scoring

/**
 * What is known about the files themselves, apart from the history of their changes.
 *
 * Both are read from what ingestion measured when it read the files, so a file that was never read has
 * neither.
 *
 * @property complexities How complicated each file is, by indentation.
 * @property authorship How many of the lines each file has now each person wrote, by `git blame`.
 */
data class FileFacts(
    val complexities: Map<FileKey, Double> = emptyMap(),
    val authorship: Map<FileKey, Map<PersonKey, Int>> = emptyMap(),
)
