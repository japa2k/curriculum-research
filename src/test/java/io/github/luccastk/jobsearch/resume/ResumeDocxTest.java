package io.github.luccastk.jobsearch.resume;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

class ResumeDocxTest {

    private static final String MARKDOWN = """
            # Fulano de Tal
            fulano@example.com | São Paulo

            ## Experiência
            ### Desenvolvedor — Acme (2023–2025)
            - Construí APIs em **Java** e *Spring*
            * Migrei o banco para PostgreSQL

            ## Projetos
            - Projeto X (2026)
            """;

    @Test
    void writesOneParagraphPerLineWithoutMarkdownSyntax() throws IOException {
        try (XWPFDocument document = open(ResumeDocx.render(MARKDOWN))) {
            assertThat(texts(document)).containsExactly(
                    "Fulano de Tal",
                    "fulano@example.com | São Paulo",
                    "Experiência",
                    "Desenvolvedor — Acme (2023–2025)",
                    "• Construí APIs em Java e Spring",
                    "• Migrei o banco para PostgreSQL",
                    "Projetos",
                    "• Projeto X (2026)");
        }
    }

    @Test
    void setsHeadingsInBold() throws IOException {
        try (XWPFDocument document = open(ResumeDocx.render(MARKDOWN))) {
            XWPFParagraph name = document.getParagraphs().get(0);
            XWPFParagraph section = document.getParagraphs().get(2);
            XWPFParagraph bullet = document.getParagraphs().get(4);

            assertThat(name.getRuns()).allMatch(XWPFRun::isBold);
            assertThat(section.getRuns()).allMatch(XWPFRun::isBold);
            assertThat(bullet.getRuns()).noneMatch(XWPFRun::isBold);
        }
    }

    @Test
    void isAtsFriendlySingleColumnWithExtractableText() throws IOException {
        try (XWPFDocument document = open(ResumeDocx.render(MARKDOWN))) {
            assertThat(document.getTables()).isEmpty();
            assertThat(document.getHeaderList()).isEmpty();
            assertThat(document.getFooterList()).isEmpty();
            assertThat(document.getAllPictures()).isEmpty();
            assertThat(document.getDocument().getBody().xmlText()).doesNotContain("txbxContent").doesNotContain("w:cols ");
            try (XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                assertThat(extractor.getText()).contains("Fulano de Tal").contains("Construí APIs em Java e Spring");
            }
        }
    }

    private static XWPFDocument open(byte[] docx) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(docx));
    }

    private static List<String> texts(XWPFDocument document) {
        return document.getParagraphs().stream().map(XWPFParagraph::getText).toList();
    }
}
