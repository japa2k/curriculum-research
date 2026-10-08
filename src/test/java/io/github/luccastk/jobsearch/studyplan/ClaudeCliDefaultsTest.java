package io.github.luccastk.jobsearch.studyplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/** The {@code claude-cli.*} defaults shipped in {@code application.yml}. */
class ClaudeCliDefaultsTest {

    @Test
    void runsClaudeInPrintModeWithToolsDisabledAndAThreeMinuteTimeout() throws IOException {
        var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        ClaudeCliProperties properties = new Binder(ConfigurationPropertySources.from(sources))
                .bind("claude-cli", ClaudeCliProperties.class)
                .get();

        assertThat(properties.command()).isEqualTo("claude");
        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(180));
        assertThat(properties.args()).containsSubsequence("-p")
                .containsSubsequence("--tools", "")
                .contains("--strict-mcp-config", "--no-session-persistence");
    }
}
