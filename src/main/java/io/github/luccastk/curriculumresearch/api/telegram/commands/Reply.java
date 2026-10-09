package io.github.luccastk.curriculumresearch.api.telegram.commands;

public record Reply(String text, String applyUrl) {

    public static Reply text(String text) {
        return new Reply(text, null);
    }

    public static Reply withApplyButton(String text, String applyUrl) {
        return new Reply(text, applyUrl);
    }

    public boolean hasApplyButton() {
        return applyUrl != null;
    }
}
