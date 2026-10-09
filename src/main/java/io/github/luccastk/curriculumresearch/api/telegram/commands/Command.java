package io.github.luccastk.curriculumresearch.api.telegram.commands;

import java.util.List;

public interface Command {
    String name();

    List<Reply> execute(String args);
}
