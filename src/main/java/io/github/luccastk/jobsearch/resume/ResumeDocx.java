package io.github.luccastk.jobsearch.resume;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

/**
 * Turns the CLI's simple Markdown résumé into an ATS-friendly {@code .docx}: one column of plain paragraphs, with
 * no tables, text boxes, images, headers or footers. {@code #} headings become bold lines and {@code -}/{@code *}
 * items become "•" lines; emphasis markers are dropped.
 */
final class ResumeDocx {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern BULLET = Pattern.compile("^[-*+]\\s+(.*)$");
    private static final Pattern EMPHASIS = Pattern.compile("(\\*\\*|__|\\*|_)(\\S(?:.*?\\S)?)\\1");
    private static final String FONT = "Calibri";

    private ResumeDocx() {
    }

    static byte[] render(String markdown) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String rawLine : markdown.strip().split("\\R")) {
                String line = rawLine.strip();
                if (line.isEmpty()) {
                    continue;
                }
                var heading = HEADING.matcher(line);
                var bullet = BULLET.matcher(line);
                if (heading.matches()) {
                    int level = heading.group(1).length();
                    addParagraph(document, plain(heading.group(2)), true, level == 1 ? 16 : level == 2 ? 13 : 11);
                } else if (bullet.matches()) {
                    addParagraph(document, "• " + plain(bullet.group(1)), false, 11);
                } else {
                    addParagraph(document, plain(line), false, 11);
                }
            }
            document.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the résumé .docx", e);
        }
    }

    private static void addParagraph(XWPFDocument document, String text, boolean bold, int fontSize) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontFamily(FONT);
        run.setFontSize(fontSize);
    }

    private static String plain(String text) {
        return EMPHASIS.matcher(text).replaceAll("$2").replace("`", "");
    }
}
