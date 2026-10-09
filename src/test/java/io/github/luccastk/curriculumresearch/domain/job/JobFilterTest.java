package io.github.luccastk.curriculumresearch.domain.job;

import static io.github.luccastk.curriculumresearch.domain.job.JobArea.BACKEND;
import static io.github.luccastk.curriculumresearch.domain.job.JobArea.FRONTEND;
import static io.github.luccastk.curriculumresearch.domain.job.JobArea.FULLSTACK;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class JobFilterTest {
    private final JobFilter filter = new JobFilter();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Desenvolvedor Back-end Pleno | BACKEND",
            "Backend Engineer (Remote) | BACKEND",
            "Desenvolvedor Java Sênior | BACKEND",
            "Golang Developer | BACKEND",
            "Engenheiro de Software C# / .NET | BACKEND",
            "Desenvolvedor PHP Laravel | BACKEND",
            "Ruby on Rails Developer | BACKEND",
            "Python Developer - Django | BACKEND",
            "Desenvolvedor Node.js | BACKEND",
            "Rust Engineer | BACKEND",
            "Front-end Developer | FRONTEND",
            "Desenvolvedora Frontend React | FRONTEND",
            "Angular Developer | FRONTEND",
            "Desenvolvedor Vue.js | FRONTEND",
            "UI Engineer | FRONTEND",
            "Desenvolvedor Full Stack | FULLSTACK",
            "Full-Stack Engineer | FULLSTACK",
            "Desenvolvedor Back-end e Front-end | FULLSTACK",
            "Desenvolvedor Java + Angular | FULLSTACK",
            "Desenvolvedor React/Node | FULLSTACK",
            "Desenvolvedor JavaScript | FULLSTACK",
            "Web Developer | FULLSTACK",
    })
    void classifiesDeveloperTitles(String title, JobArea expected) {
        assertThat(filter.classify(title)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Analista de QA",
            "Data Engineer - Python",
            "Cientista de Dados",
            "DevOps Engineer",
            "Product Designer",
            "Tech Recruiter Java",
            "Desenvolvedor React Native",
            "Desenvolvedor Android",
            "Analista Financeiro",
            "Professor de Python",
            "JavaScript Course",
    })
    void rejectsTitlesOutsideBackendFrontendAndFullstack(String title) {
        assertThat(filter.classify(title)).isEqualTo(Optional.empty());
    }

    @Test
    void javaDoesNotMatchJavaScript() {
        assertThat(filter.classify("Desenvolvedor JavaScript")).contains(FULLSTACK);
        assertThat(filter.classify("Desenvolvedor Java")).contains(BACKEND);
    }

    @Test
    void keepsOnlyWantedAreas() {
        JobPosting backend = new JobPosting("Desenvolvedor Spring Boot", "A", "SP", "u1");
        JobPosting frontend = new JobPosting("Frontend Developer", "B", "SP", "u2");
        JobPosting qa = new JobPosting("QA Engineer", "C", "SP", "u3");

        assertThat(filter.filter(List.of(backend, frontend, qa), Set.of(BACKEND, FRONTEND)))
                .containsExactly(backend, frontend);
    }

    @Test
    void searchQueriesCoverEveryAreaAndTechnology() {
        assertThat(filter.searchQueries())
                .contains("desenvolvedor backend", "frontend developer", "desenvolvedor fullstack",
                        "desenvolvedor backend junior", "desenvolvedor frontend pleno",
                        "desenvolvedor Java", "desenvolvedor Go", "desenvolvedor React")
                .hasSize(JobArea.values().length * 5 + Technology.values().length);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Desenvolvedor Java Júnior",
            "Desenvolvedor Backend Jr.",
            "Frontend Developer - Pleno",
            "Desenvolvedor Node.js Pl",
            "Mid-level Backend Engineer",
            "Trainee Desenvolvedor Fullstack",
            "Desenvolvedor React Pleno/Sênior",
            "Desenvolvedor Java",
            "Desenvolvedor PL/SQL",
    })
    void keepsJuniorPlenoAndUnleveledTitles(String title) {
        assertThat(filter.juniorOrPleno(title)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Desenvolvedor Java Sênior",
            "Senior Backend Engineer",
            "Desenvolvedor Backend Sr.",
            "Tech Lead Java",
            "Staff Software Engineer",
            "Principal Engineer - Frontend",
            "Especialista em Desenvolvimento Java",
            "Arquiteto de Software .NET",
            "Engineering Manager",
            "Estágio em Desenvolvimento Web",
            "Frontend Developer Intern",
            "Desenvolvedor PL/SQL Sênior",
    })
    void dropsTitlesAbovePlenoOrBelowJunior(String title) {
        assertThat(filter.juniorOrPleno(title)).isFalse();
    }

    @Test
    void filterDropsSeniorJobs() {
        JobPosting junior = new JobPosting("Desenvolvedor Java Júnior", "A", "SP", "u1");
        JobPosting senior = new JobPosting("Desenvolvedor Java Sênior", "B", "SP", "u2");

        assertThat(filter.filter(List.of(junior, senior), Set.of(BACKEND))).containsExactly(junior);
    }
}
