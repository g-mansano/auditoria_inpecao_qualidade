package dev.mansano.auditoria.auditoria;

import static dev.mansano.auditoria.historico.HistoricoService.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.mansano.auditoria.anexo.AnexoRepository;
import dev.mansano.auditoria.auditoria.AuditoriaDtos.*;
import dev.mansano.auditoria.comum.NegocioException;
import dev.mansano.auditoria.historico.HistoricoService;
import dev.mansano.auditoria.historico.RegistroAlteracaoRepository;
import dev.mansano.auditoria.permissao.PermissaoService;
import dev.mansano.auditoria.seguranca.UsuarioLogado;
import dev.mansano.auditoria.template.Pergunta;
import dev.mansano.auditoria.template.TemplateChecklist;
import dev.mansano.auditoria.template.TemplateRepository;
import dev.mansano.auditoria.usuario.Papel;
import dev.mansano.auditoria.usuario.UsuarioRepository;

/**
 * Regras da auditoria. A edição é exclusiva: só quem fez o check-out, no mesmo aparelho,
 * altera cabeçalho, itens e anexos. Os demais apenas consultam.
 */
@Service
public class AuditoriaService {

    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final AuditoriaRepository repo;
    private final TemplateRepository templates;
    private final UsuarioRepository usuarios;
    private final AnexoRepository anexos;
    private final HistoricoService historico;
    private final RegistroAlteracaoRepository registros;
    private final PermissaoService permissoes;

    public AuditoriaService(AuditoriaRepository repo, TemplateRepository templates, UsuarioRepository usuarios,
            AnexoRepository anexos, HistoricoService historico, RegistroAlteracaoRepository registros,
            PermissaoService permissoes) {
        this.permissoes = permissoes;
        this.repo = repo;
        this.templates = templates;
        this.usuarios = usuarios;
        this.anexos = anexos;
        this.historico = historico;
        this.registros = registros;
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public List<AuditoriaResumo> listar(UsuarioLogado eu) {
        var acessiveis = permissoes.mapa(eu).keySet();
        return repo.findByExcluidaFalseOrderByDataInicioDescIdDesc().stream()
                .filter(a -> acessiveis.contains(a.getTemplate().getId())).map(this::resumo).toList();
    }

    @Transactional(readOnly = true)
    public AuditoriaDetalhe detalhe(Long id, UsuarioLogado eu) {
        Auditoria a = buscar(id);
        permissoes.exigirVer(eu, a.getTemplate().getId());
        return detalhe(a);
    }

    @Transactional(readOnly = true)
    public List<HistoricoDto> historico(Long id, UsuarioLogado eu) {
        permissoes.exigirVer(eu, buscar(id).getTemplate().getId());
        return registros.findByAuditoriaIdOrderByRegistradoEmDescIdDesc(id).stream().map(HistoricoDto::de).toList();
    }

    // ---------------------------------------------------------------- criação e cabeçalho

    @Transactional
    public AuditoriaDetalhe criar(NovaAuditoriaReq req, UsuarioLogado eu, String dispositivo) {
        exigirAuditor(eu);
        TemplateChecklist t = req.templateId() != null
                ? templates.findById(req.templateId()).orElseThrow(() -> NegocioException.naoEncontrado("Formulário"))
                : templates.findByAtivoTrueOrderByOrdemAscCodigoAsc().stream().findFirst()
                        .orElseThrow(() -> NegocioException.invalido("Nenhum formulário ativo cadastrado."));
        permissoes.exigirPreencher(eu, t.getId());
        if (t.isInspecaoOp()) {
            throw NegocioException.invalido("Este formulário é preenchido pela tela de inspeção.");
        }
        validarDatas(req.dataInicio(), req.dataFim());

        var a = new Auditoria(req.numero().trim(), req.dataInicio(), req.dataFim(), req.auditores().trim(),
                limpar(req.escopo()), t, eu.username());
        for (Pergunta p : t.getPerguntas()) {
            a.getItens().add(new ItemAuditoria(a, p));
        }
        // Quem cria já sai com a auditoria em check-out no próprio aparelho.
        a.checkout(usuarios.getReferenceById(eu.id()), dispositivo);
        repo.save(a);
        historico.evento(a.getId(), null, CRIACAO, t.getIdentificacao(), eu.username(),
                dispositivo);
        historico.evento(a.getId(), null, CHECKOUT, null, eu.username(), dispositivo);
        return detalhe(a);
    }

    @Transactional
    public AuditoriaDetalhe atualizarCabecalho(Long id, CabecalhoReq req, UsuarioLogado eu, String dispositivo) {
        Auditoria a = buscar(id);
        exigirCheckout(a, eu, dispositivo);
        validarDatas(req.dataInicio(), req.dataFim());
        String u = eu.username();
        historico.campo(id, null, CABECALHO, "numero", a.getNumero(), req.numero().trim(), u, dispositivo);
        historico.campo(id, null, CABECALHO, "dataInicio", a.getDataInicio(), req.dataInicio(), u, dispositivo);
        historico.campo(id, null, CABECALHO, "dataFim", a.getDataFim(), req.dataFim(), u, dispositivo);
        historico.campo(id, null, CABECALHO, "auditores", a.getAuditores(), req.auditores().trim(), u, dispositivo);
        historico.campo(id, null, CABECALHO, "escopo", a.getEscopo(), limpar(req.escopo()), u, dispositivo);
        a.setNumero(req.numero().trim());
        a.setDataInicio(req.dataInicio());
        a.setDataFim(req.dataFim());
        a.setAuditores(req.auditores().trim());
        a.setEscopo(limpar(req.escopo()));
        a.tocar();
        return detalhe(a);
    }

    // ---------------------------------------------------------------- itens

    @Transactional(noRollbackFor = ItemRemovidoException.class)
    public ItemDto atualizarItem(Long auditoriaId, Long itemId, ItemReq req, UsuarioLogado eu, String dispositivo) {
        Auditoria a = buscar(auditoriaId);
        exigirCheckout(a, eu, dispositivo);
        ItemAuditoria i = a.getItens().stream().filter(x -> x.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> NegocioException.naoEncontrado("Item"));
        if (i.isRemovido()) {
            // Envio atrasado (offline) para uma pergunta que saiu: não grava no item, mas fica no histórico.
            historico.evento(a.getId(), i.getNumero(), REVISAO_APLICADA, "Resposta recebida depois da remoção da pergunta: "
                    + resumoEnvio(req), eu.username(), dispositivo);
            throw new ItemRemovidoException("A pergunta " + i.getNumero()
                    + " foi removida na Rev. " + i.getRemovidoNaRevisao() + " do formulário.");
        }

        if (req.conformidade() != null && !a.getTemplate().aceita(req.conformidade())) {
            throw NegocioException.invalido("Resposta não prevista neste formulário: " + req.conformidade() + ".");
        }
        // Pergunta alterada numa revisão: resposta dada sobre o texto antigo (envio atrasado da fila) não vale.
        // Decide pelo texto que o aparelho mostrava, não pelo relógio dele; sem esse dado (versão antiga do app),
        // cai na comparação de horário.
        Conformidade conformidade = req.conformidade();
        if (req.perguntaVista() != null) {
            if (!Objects.equals(limpar(req.perguntaVista()), limpar(i.getPergunta()))) {
                conformidade = null;
            }
        } else if (i.getAlteradaEm() != null && req.alteradoEmCliente() != null
                && req.alteradoEmCliente().isBefore(i.getAlteradaEm())) {
            conformidade = null;
        }
        // Envio atrasado da fila offline, mais antigo que o que já está gravado: ignora.
        boolean desatualizado = req.alteradoEmCliente() != null && i.getAlteradoEmCliente() != null
                && req.alteradoEmCliente().isBefore(i.getAlteradoEmCliente());
        if (!desatualizado) {
            String u = eu.username();
            int n = i.getNumero();
            historico.campo(a.getId(), n, ITEM, "processo", i.getProcesso(), limpar(req.processo()), u, dispositivo);
            historico.campo(a.getId(), n, ITEM, "documentos", i.getDocumentos(), limpar(req.documentos()), u,
                    dispositivo);
            historico.campo(a.getId(), n, ITEM, "evidencias", i.getEvidencias(), limpar(req.evidencias()), u,
                    dispositivo);
            historico.campo(a.getId(), n, ITEM, "conformidade", i.getConformidade(), conformidade, u,
                    dispositivo);
            historico.campo(a.getId(), n, ITEM, "rnc", i.getRnc(), limpar(req.rnc()), u, dispositivo);
            historico.campo(a.getId(), n, ITEM, "orientacao", i.getOrientacao(), limpar(req.orientacao()), u,
                    dispositivo);
            historico.campo(a.getId(), n, ITEM, "responsavel", i.getResponsavel(), limpar(req.responsavel()), u,
                    dispositivo);
            historico.campo(a.getId(), n, ITEM, "local", i.getLocal(), limpar(req.local()), u, dispositivo);
            i.setProcesso(limpar(req.processo()));
            i.setDocumentos(limpar(req.documentos()));
            i.setEvidencias(limpar(req.evidencias()));
            i.setConformidade(conformidade);
            if (conformidade != null) {
                i.limparAvisoDeAlteracao();
            }
            i.setRnc(limpar(req.rnc()));
            i.setOrientacao(limpar(req.orientacao()));
            i.setResponsavel(limpar(req.responsavel()));
            i.setLocal(limpar(req.local()));
            i.marcarAtualizado(u, req.alteradoEmCliente() != null ? req.alteradoEmCliente() : Instant.now());
            a.tocar();
        }
        var anexosItem = anexos.ativosDaAuditoria(a.getId()).stream()
                .filter(x -> x.getItem().getId().equals(itemId)).map(AnexoDto::de).toList();
        return ItemDto.de(i, anexosItem);
    }

    // ---------------------------------------------------------------- check-out / check-in

    @Transactional
    public AuditoriaDetalhe checkout(Long id, UsuarioLogado eu, String dispositivo, boolean forcar) {
        exigirAuditor(eu);
        Auditoria a = buscar(id);
        permissoes.exigirPreencher(eu, a.getTemplate().getId());
        if (a.getStatus() == StatusAuditoria.CONCLUIDA) {
            throw new NegocioException(HttpStatus.CONFLICT, "CONCLUIDA", "Auditoria concluída; peça a um administrador para reabrir.");
        }
        if (a.emCheckoutPor(eu.id(), dispositivo)) {
            return detalhe(a);
        }
        if (a.getEditor() != null) {
            boolean mesmoUsuario = a.getEditor().getId().equals(eu.id());
            if (!forcar || !(mesmoUsuario || eu.isAdmin())) {
                throw new NegocioException(HttpStatus.CONFLICT, "EM_USO", mensagemEmUso(a, eu));
            }
            historico.evento(a.getId(), null, CHECKOUT_FORCADO,
                    "Retirado de " + a.getEditor().getNome() + " (aparelho " + a.getEditorDispositivo() + ")",
                    eu.username(), dispositivo);
        }
        a.checkout(usuarios.getReferenceById(eu.id()), dispositivo);
        historico.evento(a.getId(), null, CHECKOUT, null, eu.username(), dispositivo);
        return detalhe(a);
    }

    @Transactional
    public AuditoriaDetalhe checkin(Long id, UsuarioLogado eu, String dispositivo) {
        Auditoria a = buscar(id);
        if (a.getEditor() == null) {
            return detalhe(a);
        }
        if (!a.emCheckoutPor(eu.id(), dispositivo) && !eu.isAdmin()) {
            throw new NegocioException(HttpStatus.CONFLICT, "EM_USO", mensagemEmUso(a, eu));
        }
        boolean porAdmin = !a.emCheckoutPor(eu.id(), dispositivo);
        historico.evento(a.getId(), null, CHECKIN,
                porAdmin ? "Liberado pelo administrador (estava com " + a.getEditor().getNome() + ")" : null,
                eu.username(), dispositivo);
        a.liberar();
        return detalhe(a);
    }

    // ---------------------------------------------------------------- conclusão

    @Transactional
    public AuditoriaDetalhe concluir(Long id, UsuarioLogado eu, String dispositivo) {
        Auditoria a = buscar(id);
        exigirCheckout(a, eu, dispositivo);
        String pendentes = a.getItensAtivos().stream().filter(i -> i.getConformidade() == null)
                .map(i -> String.valueOf(i.getNumero())).collect(Collectors.joining(", "));
        if (!pendentes.isEmpty()) {
            throw NegocioException.invalido("Itens sem conformidade preenchida: " + pendentes + ".");
        }
        String semRnc = !a.getTemplate().isExigeRegistroNc() ? "" : a.getItensAtivos().stream()
                .filter(i -> i.getConformidade() == Conformidade.NC && i.getRnc() == null)
                .map(i -> String.valueOf(i.getNumero())).collect(Collectors.joining(", "));
        if (!semRnc.isEmpty()) {
            throw NegocioException.invalido("Itens NC sem número de registro (RNC): " + semRnc + ".");
        }
        a.congelar();
        a.setStatus(StatusAuditoria.CONCLUIDA);
        a.setConcluidoEm(Instant.now());
        a.liberar();
        a.tocar();
        historico.evento(a.getId(), null, CONCLUSAO, null, eu.username(), dispositivo);
        return detalhe(a);
    }

    @Transactional
    public AuditoriaDetalhe reabrir(Long id, UsuarioLogado eu, String dispositivo) {
        if (!eu.isAdmin()) {
            throw NegocioException.proibido("Somente administradores podem reabrir auditorias.");
        }
        Auditoria a = buscar(id);
        if (a.getStatus() != StatusAuditoria.CONCLUIDA) {
            return detalhe(a);
        }
        a.setStatus(StatusAuditoria.EM_ANDAMENTO);
        a.setConcluidoEm(null);
        a.tocar();
        historico.evento(a.getId(), null, REABERTURA, null, eu.username(), dispositivo);
        return detalhe(a);
    }

    // ---------------------------------------------------------------- perguntas removidas por revisão

    @Transactional
    public AuditoriaDetalhe marcarRemovidosVistos(Long id, UsuarioLogado eu, String dispositivo) {
        Auditoria a = buscar(id);
        exigirCheckout(a, eu, dispositivo);
        a.getItens().stream().filter(i -> i.isRemovido() && !i.isRemovidoVisto())
                .forEach(ItemAuditoria::marcarRemovidoVisto);
        return detalhe(a);
    }

    // ---------------------------------------------------------------- exclusão

    /**
     * Exclui (logicamente) uma auditoria criada por engano. Pode excluir quem a criou ou um admin,
     * desde que não esteja concluída nem em edição por outra pessoa.
     */
    @Transactional
    public void excluir(Long id, UsuarioLogado eu, String dispositivo) {
        Auditoria a = buscar(id);
        if (!eu.isAdmin() && !a.getCriadoPor().equals(eu.username())) {
            throw NegocioException.proibido("Só quem criou a auditoria ou um administrador pode excluí-la.");
        }
        permissoes.exigirPreencher(eu, a.getTemplate().getId());
        if (a.getStatus() == StatusAuditoria.CONCLUIDA) {
            throw new NegocioException(HttpStatus.CONFLICT, "CONCLUIDA",
                    "Auditoria concluída não pode ser excluída. Um administrador precisa reabri-la antes.");
        }
        if (a.getEditor() != null && !a.emCheckoutPor(eu.id(), dispositivo) && !eu.isAdmin()) {
            throw new NegocioException(HttpStatus.CONFLICT, "EM_USO", mensagemEmUso(a, eu));
        }
        a.excluir(eu.username());
        historico.evento(a.getId(), null, EXCLUSAO, null, eu.username(), dispositivo);
    }

    // ---------------------------------------------------------------- uso interno (anexos, exportação)

    @Transactional(readOnly = true)
    public Auditoria buscar(Long id) {
        return repo.findById(id).filter(a -> !a.isExcluida())
                .orElseThrow(() -> NegocioException.naoEncontrado("Auditoria"));
    }

    /** Lança 423 se o usuário/aparelho não tem o check-out da auditoria. */
    public void exigirCheckout(Auditoria a, UsuarioLogado eu, String dispositivo) {
        permissoes.exigirPreencher(eu, a.getTemplate().getId());
        if (a.getStatus() == StatusAuditoria.CONCLUIDA) {
            throw new NegocioException(HttpStatus.CONFLICT, "CONCLUIDA", "Auditoria concluída; não pode ser alterada.");
        }
        if (!a.emCheckoutPor(eu.id(), dispositivo)) {
            String msg = a.getEditor() == null
                    ? "Faça o check-out da auditoria antes de editar."
                    : mensagemEmUso(a, eu);
            throw new NegocioException(HttpStatus.LOCKED, "SEM_CHECKOUT", msg);
        }
    }

    // ---------------------------------------------------------------- auxiliares

    private AuditoriaResumo resumo(Auditoria a) {
        Map<Conformidade, Long> contagem = new EnumMap<>(Conformidade.class);
        for (Conformidade c : Conformidade.values()) {
            contagem.put(c, 0L);
        }
        List<ItemAuditoria> ativos = a.getItensAtivos();
        ativos.stream().filter(i -> i.getConformidade() != null)
                .forEach(i -> contagem.merge(i.getConformidade(), 1L, Long::sum));
        int respondidos = (int) contagem.values().stream().mapToLong(Long::longValue).sum();
        return new AuditoriaResumo(a.getId(), a.getNumero(), a.getDataInicio(), a.getDataFim(), a.getAuditores(),
                a.getStatus(), a.getTemplate().getId(), formulario(a), EditorDto.de(a), ativos.size(),
                respondidos, contagem,
                a.getAtualizadoEm());
    }

    private AuditoriaDetalhe detalhe(Auditoria a) {
        Map<Long, List<AnexoDto>> porItem = a.getId() == null ? Map.of()
                : anexos.ativosDaAuditoria(a.getId()).stream().map(AnexoDto::de)
                        .collect(Collectors.groupingBy(AnexoDto::itemId));
        List<ItemDto> itens = a.getItensAtivos().stream()
                .map(i -> ItemDto.de(i, porItem.getOrDefault(i.getId(), List.of()))).toList();
        List<ItemRemovidoDto> removidos = a.getItens().stream().filter(i -> i.isRemovido() && (i.temResposta() || porItem.containsKey(i.getId())))
                .map(i -> new ItemRemovidoDto(ItemDto.de(i, porItem.getOrDefault(i.getId(), List.of())),
                        i.getRemovidoNaRevisao(), i.isRemovidoVisto()))
                .toList();
        return new AuditoriaDetalhe(a.getId(), a.getNumero(), a.getDataInicio(), a.getDataFim(), a.getAuditores(),
                a.getEscopo(), a.getStatus(), formulario(a), FormularioDto.de(a), EditorDto.de(a),
                a.getCriadoPor(), a.getCriadoEm(),
                a.getAtualizadoEm(), a.getConcluidoEm(), itens, removidos);
    }

    private static String formulario(Auditoria a) {
        return a.isRevisaoCongelada() ? a.getSnapIdentificacao() : a.getTemplate().getIdentificacao();
    }

    private static String mensagemEmUso(Auditoria a, UsuarioLogado eu) {
        boolean mesmoUsuario = a.getEditor().getId().equals(eu.id());
        String quando = a.getCheckoutEm() == null ? "" : " desde " + FMT_HORA.format(a.getCheckoutEm());
        return mesmoUsuario
                ? "Você está editando esta auditoria em outro aparelho" + quando + "."
                : "Auditoria em edição por " + a.getEditor().getNome() + quando + ".";
    }

    private static void exigirAuditor(UsuarioLogado eu) {
        if (eu.papel() == Papel.LEITOR) {
            throw NegocioException.proibido("Seu usuário é somente leitura.");
        }
    }

    private static void validarDatas(java.time.LocalDate inicio, java.time.LocalDate fim) {
        if (fim != null && fim.isBefore(inicio)) {
            throw NegocioException.invalido("A data final não pode ser anterior à data inicial.");
        }
    }

    /** 410: a pergunta saiu numa revisão. Não desfaz a transação, para o envio atrasado ficar no histórico. */
    static class ItemRemovidoException extends NegocioException {
        ItemRemovidoException(String mensagem) {
            super(HttpStatus.GONE, "ITEM_REMOVIDO", mensagem);
        }
    }

    private static String resumoEnvio(ItemReq r) {
        var partes = new java.util.ArrayList<String>();
        if (r.conformidade() != null) partes.add("resposta " + r.conformidade());
        if (limpar(r.rnc()) != null) partes.add("registro " + r.rnc().strip());
        if (limpar(r.evidencias()) != null) partes.add("evidências: " + r.evidencias().strip());
        if (limpar(r.local()) != null) partes.add("local: " + r.local().strip());
        return partes.isEmpty() ? "(sem resposta)" : String.join(" · ", partes);
    }

    static String limpar(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
