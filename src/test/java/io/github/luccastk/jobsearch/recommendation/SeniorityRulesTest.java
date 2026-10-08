package io.github.luccastk.jobsearch.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SeniorityRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Senior Java Developer", "Desenvolvedor Sênior", "Dev Sr. Node", "SR Backend", "Tech Lead",
            "Líder Técnico", "Staff Engineer", "Principal Engineer", "Backend Specialist",
            "Especialista em Java", "Engineering Manager", "Gerente de TI", "Head of Engineering",
            "Director of Engineering", "Diretor de Tecnologia", "SENIOR FULLSTACK"})
    void flagsSeniorAndAboveTitles(String title) {
        assertThat(SeniorityRules.isSeniorOrAbove(title)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Desenvolvedor Fullstack Pleno", "Junior Java Developer", "Leadership-minded Developer",
            "Headless CMS Developer", "Seniority-agnostic Engineer", "Developer (Srv team)", "Mid-level Dev"})
    void keepsTitlesWithoutSeniorMarkers(String title) {
        assertThat(SeniorityRules.isSeniorOrAbove(title)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "Desenvolvedor Júnior                 | Mid-Senior level | JUNIOR",
            "Junior Developer                     | null             | JUNIOR",
            "Dev Jr                               | null             | JUNIOR",
            "Estágio em Desenvolvimento           | null             | JUNIOR",
            "Estagiário de TI                     | null             | JUNIOR",
            "Software Engineering Intern          | null             | JUNIOR",
            "Trainee Desenvolvimento              | null             | JUNIOR",
            "Desenvolvedor Pleno                  | Entry level      | PLENO",
            "Desenvolvedor Backend PL             | null             | PLENO",
            "Mid Software Engineer                | null             | PLENO",
            "Mid-level Software Engineer          | null             | PLENO",
            "Fullstack Developer                  | Internship       | JUNIOR",
            "Fullstack Developer                  | Entry level      | JUNIOR",
            "Fullstack Developer                  | Associate        | PLENO",
            "Fullstack Developer                  | Mid-Senior level | UNKNOWN",
            "Fullstack Developer                  | Not Applicable   | UNKNOWN",
            "Fullstack Developer                  | null             | UNKNOWN",
            "Interne Developer                    | null             | UNKNOWN",
    })
    void classifiesFromTitleFirstThenTheSeniorityCriterion(String title, String criterion, Seniority expected) {
        assertThat(SeniorityRules.classify(title, criterion)).isEqualTo(expected);
    }

    @Test
    void readsTheSeniorityCriterionRegardlessOfTheDefaultLocale() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR")); // lower-cases "I" to a dotless "ı"
        try {
            assertThat(SeniorityRules.classify("Fullstack Developer", "Internship")).isEqualTo(Seniority.JUNIOR);
        } finally {
            Locale.setDefault(original);
        }
    }
}
