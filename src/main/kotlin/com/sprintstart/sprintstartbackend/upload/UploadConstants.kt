package com.sprintstart.sprintstartbackend.upload

internal val textAndCodeExtensions = setOf(
    "java",
    "kt",
    "kts",
    "groovy",
    "js",
    "jsx",
    "ts",
    "tsx",
    "py",
    "go",
    "rs",
    "cs",
    "cpp",
    "cc",
    "cxx",
    "c",
    "php",
    "rb",
    "swift",
    "scala",
    "sql",
    "html",
    "css",
    "scss",
    "xml",
    "json",
    "yaml",
    "yml",
    "toml",
    "md",
    "txt",
    "sh",
    "bash",
    "dockerfile",
)

internal val documentExtensions = setOf(
    "pdf",
)

internal val imageExtensions = setOf(
    "png",
    "jpg",
    "jpeg",
    "webp",
)

internal val allowedExtensions =
    textAndCodeExtensions + documentExtensions + imageExtensions
