package io.github.luccastk.curriculumresearch.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobPostingTest {

    @Test
    void theIdIsThePostingNumberAtTheEndOfTheUrl() {
        assertThat(posting("https://br.linkedin.com/jobs/view/desenvolvedor-java-at-acme-4012345678").id())
                .isEqualTo("4012345678");
        assertThat(posting("https://www.linkedin.com/jobs/view/4012345678/").id()).isEqualTo("4012345678");
    }

    @Test
    void aUrlWithoutNumberGetsAStableId() {
        assertThat(posting("https://example.com/vaga").id())
                .isEqualTo(posting("https://example.com/vaga").id())
                .matches("[0-9a-f-]+");
    }

    private static JobPosting posting(String url) {
        return new JobPosting("Desenvolvedor Java", "Acme", "SP", url);
    }
}
