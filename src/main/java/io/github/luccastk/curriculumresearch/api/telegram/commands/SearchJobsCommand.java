package io.github.luccastk.curriculumresearch.api.telegram.commands;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.luccastk.curriculumresearch.infra.scheduling.JobSearchScheduler;

/** /search: runs the scheduled job search right away; the new jobs join the delivery queue. */
@Component
public class SearchJobsCommand implements Command {
    private final JobSearchScheduler scheduler;

    public SearchJobsCommand(JobSearchScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "search";
    }

    @Override
    public List<Reply> execute(String args) {
        if (!scheduler.searchNow()) {
            return List.of(Reply.text("Já tem uma busca em andamento, as vagas chegam aqui quando ela terminar."));
        }
        return List.of(Reply.text("Buscando vagas... isso leva alguns minutos, mando aqui quando terminar."));
    }
}
