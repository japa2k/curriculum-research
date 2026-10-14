package io.github.luccastk.curriculumresearch.infra.linkedin;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import io.github.luccastk.curriculumresearch.domain.job.ApplyMethod;

class LinkedInJobSourceTest {

    @Test
    void asksForEasyApplyPostingsOnly() {
        assertThat(LinkedInJobSource.searchUrl("desenvolvedor java", "Brazil", 25))
                .contains("f_AL=true")
                .contains("keywords=desenvolvedor%20java")
                .contains("start=25");
    }

    @Test
    void readsTheDescriptionKeepingItsLines() {
        var page = Jsoup.parse("""
                <section><h2 class="top-card-layout__title">Desenvolvedor Java</h2>
                <div class="show-more-less-html__markup">
                  <strong>Sobre a vaga</strong><br>Time de pagamentos.<br><br>
                  <p>Requisitos:</p><ul><li>Java 21</li><li>Spring <em>Boot</em></li></ul>
                </div></section>
                """);

        assertThat(LinkedInJobSource.description(page))
                .isEqualTo("Sobre a vaga\nTime de pagamentos.\nRequisitos:\n- Java 21\n- Spring Boot");
    }

    @Test
    void aPageWithoutDescriptionGivesEmptyText() {
        assertThat(LinkedInJobSource.description(Jsoup.parse("<p>Página não encontrada</p>"))).isEmpty();
    }

    @Test
    void tellsEasyApplyFromTheApplyButton() {
        var onsite = Jsoup.parse("""
                <button class="apply-button" data-tracking-control-name="public_jobs_apply-link-onsite">Apply</button>
                """);
        var simpleOnsite = Jsoup.parse("""
                <button class="apply-button" data-tracking-control-name="public_jobs_apply-link-simple_onsite">Apply</button>
                """);

        assertThat(LinkedInJobSource.applyMethod(onsite)).isEqualTo(ApplyMethod.EASY_APPLY);
        assertThat(LinkedInJobSource.applyMethod(simpleOnsite)).isEqualTo(ApplyMethod.EASY_APPLY);
    }

    @Test
    void tellsAnExternalApplicationFromTheSignInModal() {
        var page = Jsoup.parse("""
                <button class="sign-up-modal__outlet">Apply</button>
                <div class="contextual-sign-in-modal" data-impression-id="public_jobs_apply-link-offsite_contextual-sign-in-modal">
                  <a data-tracking-control-name="public_jobs_apply-link-offsite_contextual-sign-in-modal_join-link">Join</a>
                </div>
                """);

        assertThat(LinkedInJobSource.applyMethod(page)).isEqualTo(ApplyMethod.EXTERNAL);
    }

    @Test
    void aClosedPostingGivesAnUnknownMethod() {
        var page = Jsoup.parse("""
                <h2 class="top-card-layout__title">Desenvolvedor Java</h2>
                <figure class="closed-job"><figcaption>No longer accepting applications</figcaption></figure>
                """);

        assertThat(LinkedInJobSource.applyMethod(page))
                .isEqualTo(ApplyMethod.UNKNOWN);
    }
}
