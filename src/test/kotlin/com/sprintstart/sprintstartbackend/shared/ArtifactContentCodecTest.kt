package com.sprintstart.sprintstartbackend.shared

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Base64

class ArtifactContentCodecTest {
    @Test
    fun `text mime is stored and returned as plain UTF-8`() {
        val original = "# Hello\n\nSome markdown with unicode: café".toByteArray(Charsets.UTF_8)

        val stored = ArtifactContentCodec.encode(original, "text/markdown")
        val roundTripped = ArtifactContentCodec.decode(stored, "text/markdown")

        assertThat(stored).isEqualTo(String(original, Charsets.UTF_8))
        assertThat(roundTripped).isEqualTo(original)
    }

    @Test
    fun `binary mime round-trips exactly through Base64`() {
        val original = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0xFF.toByte())

        val stored = ArtifactContentCodec.encode(original, "image/png")
        val roundTripped = ArtifactContentCodec.decode(stored, "image/png")

        assertThat(stored).isEqualTo(Base64.getEncoder().encodeToString(original))
        assertThat(roundTripped).isEqualTo(original)
    }

    @Test
    fun `isTextMime recognizes common text and structured formats`() {
        assertThat(ArtifactContentCodec.isTextMime("text/plain")).isTrue()
        assertThat(ArtifactContentCodec.isTextMime("application/json")).isTrue()
        assertThat(ArtifactContentCodec.isTextMime("application/vnd.api+json")).isTrue()
        assertThat(ArtifactContentCodec.isTextMime("image/png")).isFalse()
        assertThat(ArtifactContentCodec.isTextMime("application/pdf")).isFalse()
        assertThat(ArtifactContentCodec.isTextMime("application/octet-stream")).isFalse()
    }
}
