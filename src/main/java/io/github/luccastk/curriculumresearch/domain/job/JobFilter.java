package io.github.luccastk.curriculumresearch.domain.job;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Classifies a job posting as backend, frontend or fullstack from its title. A title that names
 * an area ("Backend Developer") is taken at its word; otherwise the area is inferred from the
 * technologies it names, and a title mixing backend and frontend technologies is fullstack.
 * Only junior and pleno jobs are kept: a title naming a higher level (or an internship) is dropped,
 * unless it also names junior or pleno; a title naming no level at all is kept.
 */
public class JobFilter {
    private static final Pattern EXCLUDED_ROLE = TitleMatcher.compile(String.join("|",
            "qa", "quality assurance", "tester", "sdet", "analista de testes?",
            "data scien\\w*", "cientista de dados", "data engineer", "engenheiro de dados",
            "data analyst", "analista de dados", "machine learning", "ml engineer",
            "devops", "sre", "site reliability", "suporte", "support", "help ?desk",
            "designer", "product manager", "product owner", "gerente de produto",
            "scrum master", "agile coach", "recrut\\w*", "recruiter", "talent\\w*",
            "vendas", "sales", "comercial", "account executive",
            "professor", "instrutor", "instructor", "teacher", "tutor"));
    private static final Pattern DEVELOPER_ROLE = TitleMatcher.compile(String.join("|",
            "desenvolvedor\\w*", "developer", "dev", "programador\\w*", "programmer",
            "engenheiro\\w*", "engineer", "analista", "software"));
    // "pl" is pleno, but not the "pl" of "PL/SQL".
    private static final Pattern JUNIOR_OR_PLENO = TitleMatcher.compile(String.join("|",
            "junior", "jr", "trainee", "entry[ -]level", "associate",
            "pleno", "pl(?!/)", "mid(?:[ -]level)?"));
    private static final Pattern OUTSIDE_JUNIOR_OR_PLENO = TitleMatcher.compile(String.join("|",
            "senior", "sr", "lead", "lider", "staff", "principal", "especialista", "specialist",
            "arquitet[oa]", "architect", "head", "gerente", "manager", "coordenador\\w*",
            "diretor\\w*", "director", "estagi\\w*", "intern(?:ship)?"));

    public Optional<JobArea> classify(String title) {
        String normalized = TitleMatcher.normalize(title);
        if (EXCLUDED_ROLE.matcher(normalized).find()) {
            return Optional.empty();
        }
        return Optional.ofNullable(namedArea(normalized))
                .or(() -> inferredArea(normalized));
    }

    public List<JobPosting> filter(List<JobPosting> jobs, Set<JobArea> wanted) {
        return jobs.stream()
                .filter(job -> juniorOrPleno(job.title()))
                .filter(job -> classify(job.title()).filter(wanted::contains).isPresent())
                .toList();
    }

    public boolean juniorOrPleno(String title) {
        String normalized = TitleMatcher.normalize(title);
        return JUNIOR_OR_PLENO.matcher(normalized).find()
                || !OUTSIDE_JUNIOR_OR_PLENO.matcher(normalized).find();
    }

    /** Queries for each area plus one per technology, to cast a wide net before filtering. */
    public List<String> searchQueries() {
        return Stream.concat(
                Stream.of(JobArea.values()).flatMap(area -> area.searchQueries().stream()),
                Stream.of(Technology.values()).map(tech -> "desenvolvedor " + tech.label()))
                .toList();
    }

    private JobArea namedArea(String title) {
        if (JobArea.FULLSTACK.namedIn(title)) {
            return JobArea.FULLSTACK;
        }
        boolean backend = JobArea.BACKEND.namedIn(title);
        boolean frontend = JobArea.FRONTEND.namedIn(title);
        if (backend && frontend) {
            return JobArea.FULLSTACK;
        }
        return backend ? JobArea.BACKEND : frontend ? JobArea.FRONTEND : null;
    }

    private Optional<JobArea> inferredArea(String title) {
        List<Technology> named = Stream.of(Technology.values())
                .filter(tech -> tech.namedIn(title))
                .toList();
        Set<JobArea> areas = EnumSet.noneOf(JobArea.class);
        named.forEach(tech -> areas.addAll(tech.areas()));

        if (areas.size() > 1) {
            return Optional.of(JobArea.FULLSTACK);
        }
        if (areas.size() == 1) {
            return Optional.of(areas.iterator().next());
        }
        // Only layer-agnostic technologies ("Desenvolvedor JavaScript", "Web Developer"):
        // a generalist web role, as long as the title is clearly a developer position.
        boolean generalistWebRole = !named.isEmpty() && DEVELOPER_ROLE.matcher(title).find();
        return generalistWebRole ? Optional.of(JobArea.FULLSTACK) : Optional.empty();
    }
}
