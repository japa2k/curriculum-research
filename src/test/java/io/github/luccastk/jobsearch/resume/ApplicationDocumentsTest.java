package io.github.luccastk.jobsearch.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.luccastk.jobsearch.studyplan.StudyPlanGenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationDocumentsTest {

    @Test
    void splitsTheOutputIntoTheThreeTrimmedDocuments() {
        ApplicationDocuments documents = ApplicationDocuments.parse("""
                ===CURRICULO===
                # Fulano
                - Java

                ===PLANO_DE_ESTUDOS===
                ## Semana 1
                ===PROJETO===
                # Projeto X
                """);

        assertThat(documents.resume()).isEqualTo("# Fulano\n- Java");
        assertThat(documents.studyPlan()).isEqualTo("## Semana 1");
        assertThat(documents.project()).isEqualTo("# Projeto X");
    }

    @Test
    void ignoresTextBeforeTheFirstMarker() {
        ApplicationDocuments documents = ApplicationDocuments.parse(
                "Claro! Aqui está:\n===CURRICULO===\nA\n===PLANO_DE_ESTUDOS===\nB\n===PROJETO===\nC");

        assertThat(documents.resume()).isEqualTo("A");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "só um texto qualquer",
            "===CURRICULO===\nA\n===PLANO_DE_ESTUDOS===\nB",
            "===CURRICULO===\nA\n===PROJETO===\nC\n===PLANO_DE_ESTUDOS===\nB",
            "===CURRICULO===\n \n===PLANO_DE_ESTUDOS===\nB\n===PROJETO===\nC",
            "===CURRICULO===\nA\n===PLANO_DE_ESTUDOS===\nB\n===PROJETO===\n  ",
            "===CURRICULO===\nA\n===CURRICULO===\nA\n===PLANO_DE_ESTUDOS===\nB\n===PROJETO===\nC"})
    void failsWhenTheOutputCannotBeSplitIntoTheThreeDocuments(String output) {
        assertThatThrownBy(() -> ApplicationDocuments.parse(output))
                .isInstanceOf(StudyPlanGenerationException.class);
    }
}
