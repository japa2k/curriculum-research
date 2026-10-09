package io.github.luccastk.curriculumresearch.domain.job;

import static io.github.luccastk.curriculumresearch.domain.job.JobArea.BACKEND;
import static io.github.luccastk.curriculumresearch.domain.job.JobArea.FRONTEND;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Technologies that, when named in a job title, reveal its area. Technologies with no area
 * (JavaScript, TypeScript, GraphQL, web) are used by every layer, so they only count when the
 * title names nothing more specific.
 */
public enum Technology {
    // Backend: JVM
    JAVA("Java", "java", BACKEND),
    SPRING("Spring Boot", "spring(?: boot)?", BACKEND),
    KOTLIN("Kotlin", "kotlin", BACKEND),
    SCALA("Scala", "scala", BACKEND),
    CLOJURE("Clojure", "clojure", BACKEND),
    GROOVY("Groovy", "groovy|grails", BACKEND),
    QUARKUS("Quarkus", "quarkus|micronaut", BACKEND),

    // Backend: .NET
    DOTNET(".NET", "c#|\\.net(?: core)?|dotnet|asp\\.net", BACKEND),

    // Backend: JavaScript runtimes
    NODE("Node.js", "node(?:\\.?js)?|deno|bun", BACKEND),
    NESTJS("NestJS", "nest(?:\\.?js)?", BACKEND),
    EXPRESS("Express", "express(?:\\.?js)?|fastify|koa|adonis(?:js)?", BACKEND),

    // Backend: scripting
    PYTHON("Python", "python", BACKEND),
    DJANGO("Django", "django", BACKEND),
    FASTAPI("FastAPI", "fastapi|flask", BACKEND),
    RUBY("Ruby", "ruby|rails|ror", BACKEND),
    PHP("PHP", "php", BACKEND),
    LARAVEL("Laravel", "laravel|symfony|codeigniter|cakephp|wordpress", BACKEND),
    PERL("Perl", "perl", BACKEND),

    // Backend: systems and functional
    GO("Go", "golang|go(?!-)", BACKEND),
    RUST("Rust", "rust", BACKEND),
    CPP("C++", "c\\+\\+", BACKEND),
    ELIXIR("Elixir", "elixir|phoenix|erlang", BACKEND),
    HASKELL("Haskell", "haskell|ocaml|f#", BACKEND),
    DELPHI("Delphi", "delphi", BACKEND),

    // Backend: concepts
    API("APIs", "apis?|microsservicos?|microservices?", BACKEND),
    SQL("SQL", "sql|pl/sql|plsql|t-sql", BACKEND),

    // Frontend: frameworks ("react native" is mobile, not frontend)
    REACT("React", "react(?:\\.?js)?(?! native)", FRONTEND),
    NEXTJS("Next.js", "next(?:\\.?js)", FRONTEND),
    ANGULAR("Angular", "angular(?:js)?", FRONTEND),
    VUE("Vue.js", "vue(?:\\.?js)?|nuxt(?:\\.?js)?", FRONTEND),
    SVELTE("Svelte", "svelte(?:kit)?", FRONTEND),
    SOLID("SolidJS", "solid(?:\\.?js)|qwik|astro|remix|gatsby|ember(?:\\.?js)?", FRONTEND),
    JQUERY("jQuery", "jquery", FRONTEND),

    // Frontend: markup and styling
    HTML("HTML/CSS", "html5?|css3?|sass|scss|tailwind", FRONTEND),

    // Every layer
    JAVASCRIPT("JavaScript", "javascript|js"),
    TYPESCRIPT("TypeScript", "typescript|ts"),
    GRAPHQL("GraphQL", "graphql"),
    WEB("Web", "web");

    private final String label;
    private final Pattern alias;
    private final Set<JobArea> areas;

    Technology(String label, String aliases, JobArea... areas) {
        this.label = label;
        this.alias = TitleMatcher.compile(aliases);
        this.areas = areas.length == 0 ? EnumSet.noneOf(JobArea.class) : EnumSet.of(areas[0], areas);
    }

    boolean namedIn(String normalizedTitle) {
        return alias.matcher(normalizedTitle).find();
    }

    public String label() {
        return label;
    }

    public Set<JobArea> areas() {
        return areas;
    }
}
