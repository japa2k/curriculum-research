package io.github.luccastk.curriculumresearch.infra.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import io.github.luccastk.curriculumresearch.domain.application.DocumentRenderer.Style;

class HtmlPdfRendererTest {
    private final HtmlPdfRenderer renderer = new HtmlPdfRenderer();

    @Test
    void rendersAResumeWithSelectableText() throws Exception {
        byte[] pdf = renderer.toPdf("""
                <header><p class="role">Desenvolvedor Fullstack</p><h1>Luccas Kobayashi</h1></header>
                <h2>Experiência</h2>
                <div class="entry"><h3>Hydra Launcher — Frontend</h3><p class="when">out/2025 – abr/2026</p>
                <ul><li>Interfaces em React e Next.js com TypeScript</li></ul></div>
                """, Style.RESUME);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            // Headings are upper-cased by the stylesheet, but must still extract as whole words.
            assertThat(text).containsSubsequence("Luccas Kobayashi", "EXPERIÊNCIA", "Hydra Launcher — Frontend",
                    "out/2025 – abr/2026", "Interfaces em React e Next.js");
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void rendersAStudyPlan() throws Exception {
        byte[] pdf = renderer.toPdf("<h1>Plano</h1><h3>Semana 1 — Spring Boot</h3><p>Ação</p>", Style.STUDY_PLAN);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document)).contains("Semana 1 — Spring Boot");
        }
    }

    @Test
    void keepsOnlyTextMarkup() {
        String cleaned = HtmlPdfRenderer.clean("""
                <h1 style="color:red" onclick="x()">Plano</h1>
                <script>alert(1)</script><style>body{}</style>
                <img src="http://169.254.169.254/latest"><link rel="stylesheet" href="http://evil/x.css">
                <a href="file:///C:/Windows/win.ini">arquivo</a><a href="https://spring.io">Spring</a>
                <p class="when">2026</p>
                """);

        assertThat(cleaned)
                .contains("<h1>Plano</h1>", "<a href=\"https://spring.io\">Spring</a>", "<p class=\"when\">2026</p>")
                .doesNotContain("script", "style", "onclick", "img", "169.254", "link", "file:");
    }
}
