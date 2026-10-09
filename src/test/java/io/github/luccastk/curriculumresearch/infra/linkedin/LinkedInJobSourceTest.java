package io.github.luccastk.curriculumresearch.infra.linkedin;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class LinkedInJobSourceTest {

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
}
