package io.github.luccastk.curriculumresearch.api.telegram;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.github.luccastk.curriculumresearch.api.telegram.commands.Command;
import io.github.luccastk.curriculumresearch.api.telegram.commands.Reply;

@Component
public class CommandHandlers {
    private final Map<String, Command> commands;

    public CommandHandlers(List<Command> commands) {
        this.commands = commands.stream().collect(java.util.stream.Collectors.toMap(Command::name, command -> command));
    }

    public List<Reply> HandleCommand(String commandName, String args) {
        Command command = commands.get(commandName);
        if (command != null) {
            return command.execute(args);
        } else {
            return List.of(Reply.text("Unknown command: " + commandName));
        }
    }
}
