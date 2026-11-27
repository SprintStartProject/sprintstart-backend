package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FileKindTest {
    @ParameterizedTest
    @CsvSource(
        "src/App.kt, CODE",
        "scripts/run.sh, CODE",
        "Makefile, CODE",
        "docs/setup.md, PROSE",
        "docs/Guide.ADOC, PROSE",
        "README, PROSE",
        "notes.txt, PROSE",
        "k8s/deploy.yaml, CONFIG",
        "package.json, CONFIG",
        "src/main/resources/application.properties, CONFIG",
        "infra/main.tf, CONFIG",
        "Dockerfile, CONFIG",
        ".env, CONFIG",
        ".editorconfig, CONFIG",
        ".github/workflows/ci.yml, CONFIG",
    )
    fun `the kind of a file is told by its name`(path: String, kind: FileKind) {
        assertThat(FileKind.of(path)).isEqualTo(kind)
    }
}
