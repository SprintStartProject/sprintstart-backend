package com.sprintstart.sprintstartbackend.ingestion.util

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FileMeasureTest {
    @Test
    fun `code is measured by its indentation`() {
        val measure = FileMeasure.of("a.kt", "fun a() {\n    b()\n}\n")

        assertThat(measure).isEqualTo(FileMeasure.Measure(FileKind.CODE, 1.0, 1.0, 3))
    }

    @Test
    fun `text is measured by its words, not by how it is indented`() {
        val measure = FileMeasure.of("docs/setup.md", "# Set up\n\n    Run `./gradlew bootRun` once, then log in.\n")

        assertThat(measure.kind).isEqualTo(FileKind.PROSE)
        assertThat(measure.complexity).isEqualTo(9.0)
        assertThat(measure.indentation).isEqualTo(1.0)
        assertThat(measure.lines).isEqualTo(2)
    }

    @Test
    fun `words of any script and digits count`() {
        assertThat(FileMeasure.of("a.md", "Größe über 42 Zeilen").complexity).isEqualTo(4.0)
    }

    @Test
    fun `settings are counted by the lines that set something`() {
        val yaml = "# the app\napp:\n  port: 8080\n\n  name: demo\n---\n"

        assertThat(FileMeasure.of("app.yaml", yaml).complexity).isEqualTo(3.0)
    }

    @Test
    fun `comments and bare brackets set nothing`() {
        val json = "{\n  \"a\": 1,\n  \"b\": [\n    2\n  ]\n}\n"
        val properties = "; ini comment\n// note\n<!-- xml -->\n-- sql\n* star\n/* block */\nkey=value\n"

        assertThat(FileMeasure.of("a.json", json).complexity).isEqualTo(3.0)
        assertThat(FileMeasure.of("a.ini", properties).complexity).isEqualTo(1.0)
    }

    @Test
    fun `an empty file holds nothing of any kind`() {
        assertThat(FileMeasure.of("a.md", "").complexity).isZero()
        assertThat(FileMeasure.of("a.yaml", "").complexity).isZero()
        assertThat(FileMeasure.of("a.kt", "").complexity).isZero()
    }
}
