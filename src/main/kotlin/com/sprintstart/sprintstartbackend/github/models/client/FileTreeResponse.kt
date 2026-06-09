package com.sprintstart.sprintstartbackend.github.models.client

import kotlinx.serialization.Serializable

@Serializable
data class FileTreeResponse(
    val sha: String,
    val url: String,
    val tree: List<TreeEntry>,
    val truncated: Boolean,
)

@Serializable
data class TreeEntry(
    val path: String,
    val type: String,
    val sha: String,
    val url: String,
)
