package dev.mansano.auditoria.template;

import static dev.mansano.auditoria.historico.HistoricoService.REVISAO_APLICADA;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.mansano.auditoria.anexo.AnexoRepository;
import dev.mansano.auditoria.auditoria.Auditoria;
import dev.mansano.auditoria.auditoria.AuditoriaRepository;
import dev.mansano.auditoria.auditoria.ItemAuditoria;
import dev.mansano.auditoria.auditoria.StatusAuditoria;
import dev.mansano.auditoria.comum.NegocioException;
import dev.mansano.auditoria.historico.HistoricoService;
import dev.mansano.auditoria.permissao.PermissaoService;
import dev.mansano.auditoria.seguranca.UsuarioLogado;
import dev.mansano.auditoria.template.RevisaoDtos.*;
import dev.mansano.auditoria.usuario.Usuario;
import dev.mansano.auditoria.usuario.UsuarioRepository;

/**
 * Revisão de formulário pelo app. A revisão é editada no lugar: "nova" sobe o número uma vez,
 * salvar de novo reescreve a mesma revisão. As auditorias em andamento (nunca concluídas)
 * recebem as perguntas novas; as concluídas ou reabertas ficam na revisão em que foram feitas.
 */
@Service
public class RevisaoService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm").withZone(FUSO);
    private static final java.util.regex.Pattern REV = java.util.regex.Pattern.compile("(Rev\\.:?\\s*)\\d+");
    private static final java.util.regex.Pattern DATA = java.util.regex.Pattern.compile("(Data:\\s*)\\d{2}/\\d{2}/\\d{4}");
    private static final Set<String> SECOES = Set.of("CHECKLIST", "CAMPO");

    private final TemplateRepository templates;
    private final PerguntaRepository perguntas;
    private final AuditoriaRepository auditorias;
    private final AnexoRepository anexos;
    private final RevisaoFormularioRepository registros;
    private final RevisaoFormularioDetalheRepository detalhes;
    private final UsuarioRepository usuarios;
    private final HistoricoService historico;
    private final PermissaoService permissoes;

    public RevisaoService(TemplateRepository templates, PerguntaRepository perguntas, AuditoriaRepository auditorias,
            AnexoRepository anexos, RevisaoFormularioRepository registros, UsuarioRepository usuarios,
            HistoricoService historico, RevisaoFormularioDetalheRepository detalhes,
            PermissaoService permissoes) {
        this.detalhes = detalhes;
        this.templates = templates;
        this.perguntas = perguntas;
        this.auditorias = auditorias;
        this.anexos = anexos;
        this.registros = registros;
        this.usuarios = usuarios;
        this.historico = historico;
        this.permissoes = permissoes;
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public RevisaoDto carregar(Long templateId, UsuarioLogado eu) {
        exigirRevisor(eu);
        permissoes.exigirVer(eu, templateId);
        TemplateChecklist t = buscar(templateId);
        Map<Long, Integer> respondidas = new HashMap<>();
        for (Auditoria a : emAndamento(t)) {
            Set<Long> comAnexo = itensComAnexo(a);
            for (ItemAuditoria i : a.getItensAtivos()) {
                if (i.getPerguntaId() != null && (i.temResposta() || comAnexo.contains(i.getId()))) {
                    respondidas.merge(i.getPerguntaId(), 1, Integer::sum);
                }
            }
        }
        var lista = t.getPerguntas().stream()
                .map(p -> new PerguntaRevDto(p.getId(), p.getNumero(), p.getSecao(), p.getGrupo(), p.getClausula(),
                        p.getTexto(), p.getProcessoPadrao(), p.getDocumentosPadrao(), p.getOrientacaoPadrao(),
                        p.getResponsavelPadrao(), respondidas.getOrDefault(p.getId(), 0)))
                .toList();
        return new RevisaoDto(t.getId(), t.getNome(), t.getCodigo(), t.getRevisao(), t.getTitulo(), t.getRodape(),
                t.getVersao(), lista);
    }

    // ---------------------------------------------------------------- prévia e salvamento

    /** O que o salvamento faria, sem gravar (resumo antes de confirmar). */
    @Transactional(readOnly = true)
    public ResultadoRevisao previa(Long templateId, SalvarRevisaoReq req, UsuarioLogado eu) {
        exigirRevisor(eu);
        permissoes.exigirPreencher(eu, templateId);
        TemplateChecklist t = buscar(templateId);
        Plano plano = planejar(t, req);
        String rev = proximaRevisao(t, req.nova());
        int reabertos = 0;
        List<Auditoria> afetadas = emAndamento(t);
        for (Auditoria a : afetadas) {
            for (ItemAuditoria i : a.getItensAtivos()) {
                if (i.getConformidade() != null && plano.alteradas.contains(i.getPerguntaId())) {
                    reabertos++;
                }
            }
        }
        return new ResultadoRevisao(rev, novoRodape(t.getRodape(), rev), plano.alteradas.size(), plano.novas,
                plano.removidas.size(), afetadas.size(), reabertos);
    }

    @Transactional
    public ResultadoRevisao salvar(Long templateId, SalvarRevisaoReq req, UsuarioLogado eu) {
        Usuario revisor = exigirRevisor(eu);
        permissoes.exigirPreencher(eu, templateId);
        TemplateChecklist t = templates.travar(templateId).orElseThrow(() -> NegocioException.naoEncontrado("Formulário"));
        if (req.versao() != t.getVersao()) {
            String quem = registros.findFirstByTemplateIdOrderBySalvoEmDescIdDesc(t.getId())
                    .map(r -> r.getSalvoPor() + " salvou esta revisão às " + FMT_HORA.format(r.getSalvoEm()) + ".")
                    .orElse("Esta revisão foi alterada por outra pessoa.");
            throw new NegocioException(HttpStatus.CONFLICT, "REVISAO_DESATUALIZADA",
                    quem + " Recarregue para ver a versão atual.");
        }
        Plano plano = planejar(t, req);
        String rev = proximaRevisao(t, req.nova());
        String revAnterior = t.getRevisao();

        // Perguntas: atualiza por id, inclui as novas, apaga as removidas. Guarda o antes para comparar.
        Map<Long, Pergunta> antigas = t.getPerguntas().stream().collect(Collectors.toMap(Pergunta::getId, Function.identity()));
        Map<Long, Antes> antes = new HashMap<>();
        antigas.values().forEach(p -> antes.put(p.getId(), Antes.de(p)));
        // Para o histórico de revisões: texto de cada pergunta antes do salvamento.
        Map<Long, String> textoAntes = new HashMap<>();
        Map<Long, Integer> numeroAntes = new HashMap<>();
        antigas.values().forEach(p -> {
            textoAntes.put(p.getId(), descrever(p.getClausula(), p.getTexto()));
            numeroAntes.put(p.getId(), p.getNumero());
        });
        String tituloAntes = t.getTitulo();
        List<Pergunta> finais = new ArrayList<>();
        int n = 1;
        for (PerguntaReq pr : req.perguntas()) {
            Pergunta p = pr.id() == null ? new Pergunta(t) : antigas.get(pr.id());
            p.definir(n++, pr.secao().strip(), limpar(pr.grupo()), limpar(pr.clausula()), pr.texto().strip(),
                    limpar(pr.processoPadrao()), limpar(pr.documentosPadrao()), limpar(pr.orientacaoPadrao()),
                    limpar(pr.responsavelPadrao()));
            finais.add(perguntas.save(p));
        }
        List<Pergunta> removidas = plano.removidas.stream().map(antigas::get).toList();
        perguntas.deleteAll(removidas);
        t.getPerguntas().clear();
        t.getPerguntas().addAll(finais);

        t.setRevisao(rev);
        t.setTitulo(req.titulo().strip());
        t.setRodape(novoRodape(t.getRodape(), rev));
        t.incrementarVersao();

        // Auditorias em andamento recebem a revisão.
        List<Auditoria> afetadas = emAndamento(t);
        int reabertos = 0;
        for (Auditoria a : afetadas) {
            reabertos += aplicar(a, finais, antes, plano, removidas, rev, revAnterior, revisor);
        }
        RevisaoFormulario registro = registros.save(new RevisaoFormulario(t.getId(), rev, req.nova(),
                plano.alteradas.size(), plano.novas, removidas.size(), afetadas.size(), revisor.getNome()));
        if (!Objects.equals(tituloAntes, t.getTitulo())) {
            detalhes.save(new RevisaoFormularioDetalhe(registro.getId(), null, "TITULO", tituloAntes, t.getTitulo()));
        }
        for (int i = 0; i < finais.size(); i++) {
            Pergunta p = finais.get(i);
            PerguntaReq pr = req.perguntas().get(i);
            if (pr.id() == null) {
                detalhes.save(new RevisaoFormularioDetalhe(registro.getId(), p.getNumero(), "NOVA", null,
                        descrever(p.getClausula(), p.getTexto())));
            } else if (plano.alteradas.contains(p.getId())) {
                detalhes.save(new RevisaoFormularioDetalhe(registro.getId(), p.getNumero(), "ALTERADA",
                        textoAntes.get(p.getId()), descrever(p.getClausula(), p.getTexto())));
            }
        }
        for (Pergunta p : removidas) {
            detalhes.save(new RevisaoFormularioDetalhe(registro.getId(), numeroAntes.get(p.getId()), "REMOVIDA",
                    textoAntes.get(p.getId()), null));
        }
        return new ResultadoRevisao(rev, t.getRodape(), plano.alteradas.size(), plano.novas, removidas.size(),
                afetadas.size(), reabertos);
    }

    /** Leva a revisão para uma auditoria em andamento. Devolve quantos itens voltaram a pendente. */
    private int aplicar(Auditoria a, List<Pergunta> finais, Map<Long, Antes> antes, Plano plano,
            List<Pergunta> removidas, String rev, String revAnterior, Usuario revisor) {
        String u = revisor.getUsername();
        Long id = a.getId();
        Set<Long> comAnexo = itensComAnexo(a);
        Map<Long, ItemAuditoria> porPergunta = new HashMap<>();
        for (ItemAuditoria i : a.getItensAtivos()) {
            if (i.getPerguntaId() != null) {
                porPergunta.put(i.getPerguntaId(), i);
            }
        }
        historico.evento(id, null, REVISAO_APLICADA, "Formulário atualizado da Rev. " + revAnterior + " para a Rev. " + rev
                + " por " + revisor.getNome(), u, null);
        int reabertos = 0;
        for (Pergunta p : finais) {
            ItemAuditoria i = porPergunta.get(p.getId());
            if (i == null) {
                ItemAuditoria novo = new ItemAuditoria(a, p);
                a.getItens().add(novo);
                historico.evento(id, p.getNumero(), REVISAO_APLICADA, "Pergunta incluída: " + p.getTexto(), u, null);
                continue;
            }
            Antes o = antes.get(p.getId());
            if (plano.alteradas.contains(p.getId())) {
                historico.campo(id, i.getNumero(), REVISAO_APLICADA, "pergunta", i.getPergunta(), p.getTexto(), u, null);
                historico.campo(id, i.getNumero(), REVISAO_APLICADA, "clausula", i.getClausula(), p.getClausula(), u, null);
                if (i.getConformidade() != null) {
                    historico.campo(id, i.getNumero(), REVISAO_APLICADA, "conformidade", i.getConformidade(), null, u,
                            null);
                    i.exigirNovaResposta(rev);
                    reabertos++;
                }
            }
            // Valor padrão novo só entra se o inspetor não tinha mexido no campo.
            if (Objects.equals(i.getProcesso(), o.processo)) {
                i.setProcesso(p.getProcessoPadrao());
            }
            if (Objects.equals(i.getDocumentos(), o.documentos)) {
                i.setDocumentos(p.getDocumentosPadrao());
            }
            if (Objects.equals(i.getOrientacao(), o.orientacao)) {
                i.setOrientacao(p.getOrientacaoPadrao());
            }
            if (Objects.equals(i.getResponsavel(), o.responsavel)) {
                i.setResponsavel(p.getResponsavelPadrao());
            }
            // O histórico antigo cita o número de então: a renumeração e a mudança de grupo ficam registradas.
            historico.campo(id, i.getNumero(), REVISAO_APLICADA, "numero", i.getNumero(), p.getNumero(), u, null);
            historico.campo(id, p.getNumero(), REVISAO_APLICADA, "grupo", i.getGrupo(), p.getGrupo(), u, null);
            i.aplicarPergunta(p);
        }
        for (Pergunta p : removidas) {
            ItemAuditoria i = porPergunta.get(p.getId());
            if (i == null) {
                continue;
            }
            // Remoção sempre lógica: o item pode ter anexos excluídos (FK) e envios atrasados recebem 410, não 404.
            boolean respondido = i.temResposta() || comAnexo.contains(i.getId());
            historico.evento(id, i.getNumero(), REVISAO_APLICADA, "Pergunta removida na Rev. " + rev
                    + (respondido ? ": " + i.getPergunta() + resumoResposta(i) : " (sem resposta): " + i.getPergunta()),
                    u, null);
            i.remover(rev);
            if (!respondido) {
                i.marcarRemovidoVisto(); // nada a mostrar na janela de removidas
            }
        }
        a.tocar();
        return reabertos;
    }

    // ---------------------------------------------------------------- histórico de revisões

    @Transactional(readOnly = true)
    public List<HistoricoRevisaoDto> historico(Long templateId) {
        List<RevisaoFormulario> lista = registros.findByTemplateIdOrderBySalvoEmDescIdDesc(templateId);
        Map<Long, List<DetalheRevisaoDto>> porRevisao = new HashMap<>();
        if (!lista.isEmpty()) {
            detalhes.findByRevisaoIdInOrderByIdAsc(lista.stream().map(RevisaoFormulario::getId).toList())
                    .forEach(d -> porRevisao.computeIfAbsent(d.getRevisaoId(), k -> new ArrayList<>())
                            .add(new DetalheRevisaoDto(d.getNumero(), d.getTipo(), d.getAntes(), d.getDepois())));
        }
        return lista.stream()
                .map(r -> new HistoricoRevisaoDto(r.getId(), r.getRevisao(), r.isNova(), r.getAlteradas(),
                        r.getNovas(), r.getRemovidas(), r.getAuditorias(), r.getSalvoPor(), r.getSalvoEm(),
                        porRevisao.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    private static String descrever(String clausula, String texto) {
        return clausula == null ? texto : clausula + " · " + texto;
    }

    // ---------------------------------------------------------------- auxiliares

    /** O que muda entre o formulário atual e o que veio da tela. */
    private record Plano(Set<Long> alteradas, int novas, List<Long> removidas) {
    }

    private Plano planejar(TemplateChecklist t, SalvarRevisaoReq req) {
        Map<Long, Pergunta> atuais = t.getPerguntas().stream().collect(Collectors.toMap(Pergunta::getId, Function.identity()));
        Set<Long> vistos = new HashSet<>();
        Set<Long> alteradas = new HashSet<>();
        int novas = 0;
        for (PerguntaReq pr : req.perguntas()) {
            if (!SECOES.contains(pr.secao().strip())) {
                throw NegocioException.invalido("Seção inválida: " + pr.secao() + ".");
            }
            if (pr.id() == null) {
                novas++;
                continue;
            }
            Pergunta p = atuais.get(pr.id());
            if (p == null || !vistos.add(pr.id())) {
                throw NegocioException.invalido("Pergunta " + pr.id() + " não pertence a este formulário.");
            }
            if (!Objects.equals(limpar(p.getTexto()), limpar(pr.texto()))
                    || !Objects.equals(limpar(p.getClausula()), limpar(pr.clausula()))
                    || !Objects.equals(limpar(p.getSecao()), limpar(pr.secao()))) {
                alteradas.add(p.getId());
            }
        }
        List<Long> removidas = atuais.keySet().stream().filter(id -> !vistos.contains(id)).sorted().toList();
        return new Plano(alteradas, novas, removidas);
    }

    private record Antes(String processo, String documentos, String orientacao, String responsavel) {
        static Antes de(Pergunta p) {
            return new Antes(p.getProcessoPadrao(), p.getDocumentosPadrao(), p.getOrientacaoPadrao(),
                    p.getResponsavelPadrao());
        }
    }

    private static String proximaRevisao(TemplateChecklist t, boolean nova) {
        if (!nova) {
            return t.getRevisao();
        }
        int atual;
        try {
            atual = Integer.parseInt(t.getRevisao().strip());
        } catch (NumberFormatException e) {
            throw NegocioException.invalido("Revisão atual não numérica: " + t.getRevisao() + ".");
        }
        return String.format("%02d", atual + 1);
    }

    /** Troca só o número da revisão e a data (dia do salvamento); o resto do rodapé fica como no original. */
    static String novoRodape(String rodape, String rev) {
        if (rodape == null) {
            return null;
        }
        String hoje = LocalDate.now(FUSO).format(FMT_DATA);
        String r = REV.matcher(rodape).replaceFirst(m -> Matcher.quoteReplacement(m.group(1) + rev));
        return DATA.matcher(r).replaceFirst(m -> Matcher.quoteReplacement(m.group(1) + hoje));
    }

    private static String resumoResposta(ItemAuditoria i) {
        StringBuilder s = new StringBuilder();
        if (i.getConformidade() != null) {
            s.append(" · resposta: ").append(i.getConformidade());
        }
        if (i.getRnc() != null) {
            s.append(" · registro: ").append(i.getRnc());
        }
        if (i.getEvidencias() != null) {
            s.append(" · evidências: ").append(i.getEvidencias());
        }
        if (i.getLocal() != null) {
            s.append(" · local: ").append(i.getLocal());
        }
        return s.toString();
    }

    private List<Auditoria> emAndamento(TemplateChecklist t) {
        return auditorias.findByTemplateIdAndStatusAndRevisaoCongeladaFalseAndExcluidaFalse(t.getId(),
                StatusAuditoria.EM_ANDAMENTO);
    }

    private Set<Long> itensComAnexo(Auditoria a) {
        return anexos.ativosDaAuditoria(a.getId()).stream().map(x -> x.getItem().getId()).collect(Collectors.toSet());
    }

    private TemplateChecklist buscar(Long id) {
        return templates.findById(id).orElseThrow(() -> NegocioException.naoEncontrado("Formulário"));
    }

    private Usuario exigirRevisor(UsuarioLogado eu) {
        Usuario u = usuarios.findById(eu.id()).orElseThrow();
        if (!u.isPodeRevisar()) {
            throw NegocioException.proibido("Seu usuário não pode revisar formulários.");
        }
        return u;
    }

    private static String limpar(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
