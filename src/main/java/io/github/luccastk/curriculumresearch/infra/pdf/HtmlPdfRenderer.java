package io.github.luccastk.curriculumresearch.infra.pdf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import io.github.luccastk.curriculumresearch.domain.application.DocumentRenderer;

/**
 * Renders HTML bodies into PDFs with a fixed stylesheet per document. The body comes from Claude,
 * which read untrusted posting text, so it is cleaned first: only text markup survives, with no
 * scripts, styles or images, and nothing that would make the renderer load a resource.
 */
@Component
public class HtmlPdfRenderer implements DocumentRenderer {
    private static final Safelist ALLOWED = Safelist.none()
            .addTags("h1", "h2", "h3", "p", "ul", "ol", "li", "strong", "em", "b", "i", "span", "div", "a",
                    "header", "section", "br")
            .addAttributes(":all", "class")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https", "mailto");
    private static final Map<Style, String> STYLESHEETS = Map.of(
            Style.RESUME, "templates/resume.css",
            Style.STUDY_PLAN, "templates/study-plan.css");
    // System fonts, so accents and symbols outside the PDF's built-in fonts still render: Arial on
    // Windows, Liberation Sans (same metrics, package fonts-liberation) on Linux and in Docker.
    private static final String LIBERATION = "/usr/share/fonts/truetype/liberation/";
    private static final List<Font> FONTS = List.of(
            new Font("C:/Windows/Fonts/arial.ttf", 400, FontStyle.NORMAL),
            new Font("C:/Windows/Fonts/arialbd.ttf", 700, FontStyle.NORMAL),
            new Font("C:/Windows/Fonts/ariali.ttf", 400, FontStyle.ITALIC),
            new Font(LIBERATION + "LiberationSans-Regular.ttf", 400, FontStyle.NORMAL),
            new Font(LIBERATION + "LiberationSans-Bold.ttf", 700, FontStyle.NORMAL),
            new Font(LIBERATION + "LiberationSans-Italic.ttf", 400, FontStyle.ITALIC));

    private record Font(String path, int weight, FontStyle style) {
    }

    @Override
    public byte[] toPdf(String bodyHtml, Style style) {
        Document page = Jsoup.parse("""
                <!DOCTYPE html><html><head><meta charset="utf-8"/><style>%s</style></head><body>%s</body></html>
                """.formatted(stylesheet(style), clean(bodyHtml)));

        try (ByteArrayOutputStream pdf = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder()
                    .withW3cDocument(new W3CDom().fromJsoup(page), null)
                    .toStream(pdf);
            registerFonts(builder);
            builder.run();
            return pdf.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render the PDF", e);
        }
    }

    static String clean(String bodyHtml) {
        return Jsoup.clean(bodyHtml, ALLOWED);
    }

    private static String stylesheet(Style style) {
        try (InputStream css = HtmlPdfRenderer.class.getClassLoader().getResourceAsStream(STYLESHEETS.get(style))) {
            if (css == null) {
                throw new IllegalStateException("Missing stylesheet " + STYLESHEETS.get(style));
            }
            return new String(css.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void registerFonts(PdfRendererBuilder builder) {
        for (Font font : FONTS) {
            File file = new File(font.path());
            if (file.isFile()) {
                builder.useFont(file, "Body", font.weight(), font.style(), true);
            }
        }
    }
}
