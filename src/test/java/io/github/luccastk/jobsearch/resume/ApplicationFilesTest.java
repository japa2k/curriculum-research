package io.github.luccastk.jobsearch.resume;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ApplicationFilesTest {

    @TempDir
    Path dir;

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "Acme,                       curriculo-acme-4242.docx",
            "São Paulo Tecnologia S.A.,  curriculo-sao-paulo-tecnologia-s-a-4242.docx",
            "'  --Ünïcode & Co.--  ',     curriculo-unicode-co-4242.docx",
            "岩田,                        curriculo-4242.docx",
            "NULL,                       curriculo-4242.docx"})
    void namesTheResumeAfterTheCompanyAndJob(String company, String expected) {
        assertThat(ApplicationFiles.resumeFileName(company, "4242")).isEqualTo(expected);
    }

    @Test
    void writesTheThreeFilesUnderTheJobsDirectory() throws IOException {
        ApplicationFiles files = new ApplicationFiles(dir.resolve("applications"));

        files.write("4242", new byte[] {1, 2, 3}, "plano", "projeto");

        Path jobDir = dir.resolve("applications/4242");
        assertThat(Files.readAllBytes(jobDir.resolve("curriculo.docx"))).containsExactly(1, 2, 3);
        assertThat(jobDir.resolve("plano-de-estudos.md")).content(StandardCharsets.UTF_8).isEqualTo("plano");
        assertThat(jobDir.resolve("projeto.md")).content(StandardCharsets.UTF_8).isEqualTo("projeto");
        assertThat(files.read("4242")).hasValueSatisfying(saved -> {
            assertThat(saved.resume()).containsExactly(1, 2, 3);
            assertThat(new String(saved.studyPlan(), StandardCharsets.UTF_8)).isEqualTo("plano");
            assertThat(new String(saved.project(), StandardCharsets.UTF_8)).isEqualTo("projeto");
        });
    }

    @Test
    void readsNothingWhenAnyFileIsMissing() throws IOException {
        ApplicationFiles files = new ApplicationFiles(dir);
        files.write("4242", new byte[] {1}, "plano", "projeto");

        Files.delete(dir.resolve("4242/projeto.md"));

        assertThat(files.read("4242")).isEmpty();
        assertThat(files.read("9999")).isEmpty();
    }
}
