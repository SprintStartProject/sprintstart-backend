package com.sprintstart.sprintstartbackend.canonical.model

object FileMetaDataResolver {
    private val EXTENSION_TO_LANGUAGE =
        mapOf(
            "java" to "Java",
            "kt" to "Kotlin",
            "kts" to "Kotlin",
            "groovy" to "Groovy",
            "js" to "JavaScript",
            "jsx" to "JavaScript",
            "ts" to "TypeScript",
            "tsx" to "TypeScript",
            "py" to "Python",
            "go" to "Go",
            "rs" to "Rust",
            "cs" to "C#",
            "cpp" to "C++",
            "cc" to "C++",
            "cxx" to "C++",
            "c" to "C",
            "php" to "PHP",
            "rb" to "Ruby",
            "swift" to "Swift",
            "scala" to "Scala",
            "sql" to "SQL",
            "html" to "HTML",
            "css" to "CSS",
            "scss" to "SCSS",
            "xml" to "XML",
            "json" to "JSON",
            "yaml" to "YAML",
            "yml" to "YAML",
            "toml" to "TOML",
            "md" to "Markdown",
            "txt" to "Plain Text",
            "sh" to "Shell",
            "bash" to "Shell",
            "dockerfile" to "Dockerfile",
        )

    private val EXTENSION_TO_MIME =
        mapOf(
            "java" to "text/x-java",
            "kt" to "text/x-kotlin",
            "kts" to "text/x-kotlin",
            "js" to "text/javascript",
            "jsx" to "text/javascript",
            "ts" to "text/typescript",
            "tsx" to "text/typescript",
            "py" to "text/x-python",
            "go" to "text/x-go",
            "rs" to "text/x-rust",
            "html" to "text/html",
            "css" to "text/css",
            "json" to "application/json",
            "xml" to "application/xml",
            "yaml" to "application/yaml",
            "yml" to "application/yaml",
            "md" to "text/markdown",
            "txt" to "text/plain",
            "sql" to "application/sql",
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "svg" to "image/svg+xml",
            "webp" to "image/webp",
            "pdf" to "application/pdf",
            "zip" to "application/zip",
            "jar" to "application/java-archive",
        )

    fun languageFor(extension: String): String? {
        return EXTENSION_TO_LANGUAGE[extension]
    }

    fun mimeFor(extension: String): String? {
        return EXTENSION_TO_MIME[extension]
    }
}
