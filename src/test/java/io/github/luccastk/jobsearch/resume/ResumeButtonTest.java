package io.github.luccastk.jobsearch.resume;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.luccastk.jobsearch.telegram.InlineButton;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ResumeButtonTest {

    @Test
    void labelsTheButtonAndIdentifiesTheJob() {
        InlineButton button = ResumeButton.forJob("4242");

        assertThat(button.text()).isEqualTo("📄 Gerar currículo");
        assertThat(ResumeButton.jobId(button.callbackData())).contains("4242");
    }

    @Test
    void keepsTheLongestJobIdWithinTelegramsSixtyFourBytes() {
        String callbackData = ResumeButton.forJob("12345678901234567890").callbackData();

        assertThat(callbackData.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(64);
        assertThat(ResumeButton.jobId(callbackData)).contains("12345678901234567890");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"4242", "resume:", "resume:abc", "resume:12a", "resume:123456789012345678901",
            "other:4242", "resume:4242:x", " resume:4242"})
    void rejectsAnythingElseAsAPayload(String data) {
        assertThat(ResumeButton.jobId(data)).isEmpty();
    }
}
