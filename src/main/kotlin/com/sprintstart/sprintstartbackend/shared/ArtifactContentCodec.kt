package com.sprintstart.sprintstartbackend.shared

import java.util.Base64

/**
 * Encodes and decodes raw artifact bytes for storage in a text column.
 *
 * Text-like content (source code, markdown, JSON, ...) is stored as-is so it stays searchable
 * and can be fed to AI indexing directly. Binary content (images, PDFs, ...) would be corrupted
 * by that path, so it is Base64-encoded instead.
 */
object ArtifactContentCodec {
    private val TEXT_MIME_TYPES = setOf(
        "application/json",
        "application/xml",
        "application/x-yaml",
        "application/yaml",
    )

    fun isTextMime(mime: String): Boolean =
        mime.startsWith("text/") ||
            mime in TEXT_MIME_TYPES ||
            mime.endsWith("+json") ||
            mime.endsWith("+xml")

    fun encode(bytes: ByteArray, mime: String): String =
        if (isTextMime(mime)) {
            String(bytes, Charsets.UTF_8)
        } else {
            Base64.getEncoder().encodeToString(bytes)
        }

    fun decode(content: String, mime: String): ByteArray =
        if (isTextMime(mime)) {
            content.toByteArray(Charsets.UTF_8)
        } else {
            Base64.getDecoder().decode(content)
        }
}
