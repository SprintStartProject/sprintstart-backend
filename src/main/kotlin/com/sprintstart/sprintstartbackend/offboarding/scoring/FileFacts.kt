package com.sprintstart.sprintstartbackend.offboarding.scoring

/**
 * What is known about the files themselves, apart from the history of their changes.
 *
 * Both are read from what ingestion measured when it read the files, so a file that was never read has
 * neither.
 *
 * @property complexities How much each file holds, in the unit of its kind.
 * @property authorship How many of the lines each file has now each person wrote, by `git blame`.
 */
data class FileFacts(
    val complexities: Map<FileKey, Complexity> = emptyMap(),
    val authorship: Map<FileKey, Map<PersonKey, Int>> = emptyMap(),
)
