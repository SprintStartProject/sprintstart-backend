package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

/**
 * What a file holds, which decides how its complexity is measured and with which files it is compared.
 *
 * A page of documentation and a source file hold knowledge in different ways, so each kind has its own
 * measure, and a measure means something only against files of the same kind.
 */
enum class FileKind {
    /** Source code, measured by how deeply its lines are indented. */
    CODE,

    /** Written text such as documentation, measured by its words. */
    PROSE,

    /** Settings such as YAML, JSON or properties, measured by the settings it holds. */
    CONFIG,
    ;

    companion object {
        private val PROSE_EXTENSIONS = setOf("md", "markdown", "mdx", "rst", "adoc", "asciidoc", "txt", "org", "tex")
        private val PROSE_NAMES = setOf("readme", "changelog", "contributing", "authors", "notice")
        private val CONFIG_EXTENSIONS = setOf(
            "yml",
            "yaml",
            "json",
            "jsonc",
            "toml",
            "ini",
            "cfg",
            "conf",
            "properties",
            "env",
            "xml",
            "tf",
            "tfvars",
            "hcl",
        )
        private val CONFIG_NAMES = setOf("dockerfile", "containerfile", ".editorconfig", ".gitattributes", ".env")

        /** The kind of the file at [path], told by its name; code when nothing else fits. */
        fun of(path: String): FileKind {
            val name = path.substringAfterLast('/').lowercase()
            val extension = if ('.' in name.drop(1)) name.substringAfterLast('.') else ""
            return when {
                extension in PROSE_EXTENSIONS || name in PROSE_NAMES -> PROSE
                extension in CONFIG_EXTENSIONS || name in CONFIG_NAMES -> CONFIG
                else -> CODE
            }
        }
    }
}
