package io.github.luccastk.jobsearch.resume;

import static io.github.luccastk.jobsearch.studyplan.StudyPlanPrompt.orUnknown;

import io.github.luccastk.jobsearch.linkedin.JobDetail;
import io.github.luccastk.jobsearch.profile.Profile;
import io.github.luccastk.jobsearch.studyplan.StudyPlanPrompt;
import java.util.List;

/**
 * Asks the Claude CLI, in one run, for a tailored résumé, a study plan and a project brief, each after its
 * {@link ApplicationDocuments} marker. The study plan follows the same rules as {@link StudyPlanPrompt}.
 */
final class ApplicationPrompt {

    private ApplicationPrompt() {
    }

    /**
     * @param baseResume the engineer's résumé in Markdown; the source of every fact in the tailored one
     * @param year       the only date the generated project may carry
     */
    static String build(JobDetail detail, List<String> knownSkills, List<String> matchedSkills,
            List<String> missingSkills, Profile.StudyPlanSettings budget, String baseResume, int year) {
        String missingAsEntries = missingSkills.isEmpty() ? "" : """
                - Inclua na seção de skills, como itens normais, estas skills que o plano de estudos cobre: %s. \
                Não use rótulos como "estudando" ou "em andamento".
                """.formatted(String.join(", ", missingSkills));
        String projectFocus = missingSkills.isEmpty()
                ? "as skills que a vaga mais enfatiza (" + String.join(", ", matchedSkills) + ")"
                : "as skills que faltam (" + String.join(", ", missingSkills) + ")";
        return """
                Você é um especialista em carreira para desenvolvedores. Para a vaga abaixo, escreva três documentos, \
                nesta ordem, cada um logo depois do seu marcador, que fica sozinho em uma linha:
                %1$s
                %2$s
                %3$s
                Não escreva nada antes do primeiro marcador.

                Documento 1, depois de %1$s: meu currículo adaptado à vaga.
                - Escreva no idioma da descrição da vaga: em inglês se ela estiver em inglês; caso contrário, em \
                português do Brasil.
                - Parta do meu currículo base. Reordene e reescreva o conteúdo para destacar as palavras-chave da vaga.
                - Mantenha exatamente o nome e os dados de contato do currículo base.
                %4$s\
                - Inclua na seção de projetos o projeto do documento 3, datado com apenas o ano %5$d: sem mês e sem data \
                de conclusão.
                - Nunca acrescente empregador, cargo, data de emprego, formação, certificação ou métrica que não \
                esteja no currículo base.
                - Formato compatível com ATS: Markdown simples, uma coluna, `#` para o nome, `##` para títulos de \
                seção padrão (como Resumo, Experiência, Projetos, Formação, Skills), `###` para cada cargo ou \
                projeto e `- ` para itens; sem tabelas, colunas, caixas de texto ou imagens.

                Documento 2, depois de %2$s: um plano de estudos para eu me candidatar à vaga.
                %6$s
                Documento 3, depois de %3$s: um projeto de portfólio que exercite %7$s, em Markdown, em português \
                do Brasil. Inclua: nome, problema que resolve, stack, escopo e funcionalidades, o que cada parte \
                demonstra, e de 3 a 5 pontos para conversar com o recrutador sobre ele.

                Meu perfil:
                - Skills que já tenho: %8$s
                - %9$s

                Meu currículo base:
                <curriculo_base>
                %10$s
                </curriculo_base>

                A vaga:
                - Título: %11$s
                - Empresa: %12$s

                %13$s""".formatted(
                ApplicationDocuments.RESUME_MARKER,
                ApplicationDocuments.STUDY_PLAN_MARKER,
                ApplicationDocuments.PROJECT_MARKER,
                missingAsEntries,
                year,
                StudyPlanPrompt.planRules(budget),
                projectFocus,
                String.join(", ", knownSkills),
                StudyPlanPrompt.gap(missingSkills),
                baseResume,
                orUnknown(detail.title()),
                orUnknown(detail.company()),
                StudyPlanPrompt.untrustedDescription(detail.description()));
    }
}
