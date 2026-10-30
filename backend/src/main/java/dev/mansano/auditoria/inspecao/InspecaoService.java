package dev.mansano.auditoria.inspecao;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import dev.mansano.auditoria.anexo.AnexoService;
import dev.mansano.auditoria.comum.NegocioException;
import dev.mansano.auditoria.inspecao.InspecaoDtos.*;
import dev.mansano.auditoria.permissao.PermissaoService;
import dev.mansano.auditoria.seguranca.UsuarioLogado;
import dev.mansano.auditoria.template.Pergunta;
import dev.mansano.auditoria.template.TemplateChecklist;
import dev.mansano.auditoria.template.TemplateRepository;
import dev.mansano.auditoria.usuario.UsuarioRepository;

/**
 * Inspeção de OPs (FQ-106). Uma inspeção por inspetor por dia, com várias OPs.
 * Quem tem "Preencher" abre a sua; só o dono (ou o admin) altera; quem tem "Ver" consulta todas.
 * Ids de inspeção, OP, lote e anexo vêm do aparelho (funciona offline; reenvio é idempotente).
 */
@Service
public class InspecaoService {

    public static final String CRIACAO = "CRIACAO";
    public static final String OP_INCLUIDA = "OP_INCLUIDA";
    public static final String OP_ALTERADA = "OP_ALTERADA";
    public static final String OP_EXCLUIDA = "OP_EXCLUIDA";
    public static final String LOTE_INCLUIDO = "LOTE_INCLUIDO";
    public static final String LOTE_REMOVIDO = "LOTE_REMOVIDO";
    public static final String ANEXO_INCLUIDO = "ANEXO_INCLUIDO";
    public static final String ANEXO_EXCLUIDO = "ANEXO_EXCLUIDO";
    public static final String FINALIZACAO = "FINALIZACAO";
    public static final String REABERTURA = "REABERTURA";
    public static final String EXCLUSAO = "EXCLUSAO";

    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InspecaoDiaRepository dias;
    private final AnexoInspecaoRepository anexos;
    private final RegistroInspecaoRepository registros;
    private final TemplateRepository templates;
    private final UsuarioRepository usuarios;
    private final PermissaoService permissoes;
    private final AnexoService arquivos;

    public InspecaoService(InspecaoDiaRepository dias, AnexoInspecaoRepository anexos,
            RegistroInspecaoRepository registros, TemplateRepository templates, UsuarioRepository usuarios,
            PermissaoService permissoes, AnexoService arquivos) {
        this.dias = dias;
        this.anexos = anexos;
        this.registros = registros;
        this.templates = templates;
        this.usuarios = usuarios;
        this.permissoes = permissoes;
        this.arquivos = arquivos;
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public List<DiaResumo> listar(UsuarioLogado eu) {
        Set<Long> acessiveis = permissoes.mapa(eu).keySet();
        List<Long> inspecao = templates.findAll().stream().filter(TemplateChecklist::isInspecaoOp)
                .map(TemplateChecklist::getId).filter(acessiveis::contains).toList();
        if (inspecao.isEmpty()) {
            return List.of();
        }
        return dias.findByTemplateIdInAndExcluidaFalseOrderByDataDescCriadoEmDesc(inspecao).stream().map(this::resumo).toList();
    }

    @Transactional(readOnly = true)
    public DiaDetalhe detalhe(String id, UsuarioLogado eu) {
        InspecaoDia d = buscar(id);
        permissoes.exigirVer(eu, d.getTemplate().getId());
        return detalhe(d);
    }

    @Transactional(readOnly = true)
    public List<HistoricoDto> historico(String id, UsuarioLogado eu) {
        permissoes.exigirVer(eu, buscar(id).getTemplate().getId());
        return registros.findByDiaIdOrderByRegistradoEmDescIdDesc(id).stream().map(HistoricoDto::de).toList();
    }

    // ---------------------------------------------------------------- inspeção do dia

    /** Abre a inspeção do dia (id do aparelho). Reenvio do mesmo id devolve a existente. */
    @Transactional
    public DiaDetalhe abrirDia(String id, NovoDiaReq req, UsuarioLogado eu, String dispositivo) {
        validarId(id);
        var existente = dias.findById(id);
        if (existente.isPresent()) {
            if (existente.get().isExcluida()) {
                throw NegocioException.naoEncontrado("Inspeção");
            }
            permissoes.exigirVer(eu, existente.get().getTemplate().getId());
            return detalhe(existente.get());
        }
        TemplateChecklist t = templates.findById(req.templateId())
                .orElseThrow(() -> NegocioException.naoEncontrado("Formulário"));
        if (!t.isInspecaoOp()) {
            throw NegocioException.invalido("Este formulário não é de inspeção de OPs.");
        }
        permissoes.exigirPreencher(eu, t.getId());
        dias.findByTemplateIdAndInspetorIdAndDataAndExcluidaFalse(t.getId(), eu.id(), req.data()).ifPresent(outra -> {
            throw new NegocioException(HttpStatus.CONFLICT, "INSPECAO_EXISTENTE",
                    "Você já tem a inspeção de " + FMT_DATA.format(req.data()) + " (" + outra.getId() + ").");
        });
        InspecaoDia d = dias.save(new InspecaoDia(id, t, req.data(), usuarios.getReferenceById(eu.id())));
        registrar(d, null, CRIACAO, null, null, t.getIdentificacao() + " · " + FMT_DATA.format(req.data()), eu,
                dispositivo);
        return detalhe(d);
    }

    @Transactional
    public DiaDetalhe finalizar(String id, UsuarioLogado eu, String dispositivo) {
        InspecaoDia d = buscar(id);
        exigirEditar(d, eu);
        d.finalizar();
        registrar(d, null, FINALIZACAO, null, null, d.getOpsAtivas().size() + " OP(s)", eu, dispositivo);
        return detalhe(d);
    }

    @Transactional
    public DiaDetalhe reabrir(String id, UsuarioLogado eu, String dispositivo) {
        if (!eu.isAdmin()) {
            throw NegocioException.proibido("Somente administradores podem reabrir inspeções.");
        }
        InspecaoDia d = buscar(id);
        if (d.getStatus() == StatusInspecao.FINALIZADA) {
            d.reabrir();
            registrar(d, null, REABERTURA, null, null, null, eu, dispositivo);
        }
        return detalhe(d);
    }

    /** Só o admin (ex.: inspeções de teste). Exclusão lógica: some das listas, o registro e o histórico ficam. */
    @Transactional
    public void excluir(String id, UsuarioLogado eu, String dispositivo) {
        if (!eu.isAdmin()) {
            throw NegocioException.proibido("Somente administradores podem excluir inspeções.");
        }
        InspecaoDia d = buscar(id);
        d.excluir(eu.username());
        registrar(d, null, EXCLUSAO, null, null, d.getOpsAtivas().size() + " OP(s)", eu, dispositivo);
    }

    // ---------------------------------------------------------------- OPs

    /** Cria ou atualiza a OP inteira. Envio atrasado da fila (mais antigo que o gravado) é ignorado. */
    @Transactional
    public OpDto salvarOp(String diaId, String opId, OpReq req, UsuarioLogado eu, String dispositivo) {
        validarId(opId);
        InspecaoDia d = buscar(diaId);
        exigirEditar(d, eu);
        InspecaoOp op = d.getOps().stream().filter(o -> o.getId().equals(opId)).findFirst().orElse(null);
        if (op == null) {
            op = new InspecaoOp(opId, d, req.ordem());
            // A OP copia os tópicos que o aparelho mostrou ao criá-la (pode ter sido offline, antes de uma revisão);
            // sem esses dados (envio antigo), usa os tópicos atuais do formulário.
            boolean doAparelho = req.itens() != null && !req.itens().isEmpty()
                    && req.itens().stream().allMatch(i -> limpar(i.topico()) != null);
            if (doAparelho) {
                for (ItemReq ir : req.itens()) {
                    op.getItens().add(new InspecaoItem(op, ir.numero(), ir.topico().strip(), limpar(ir.escala())));
                }
            } else {
                for (Pergunta p : d.getTemplate().getPerguntas()) {
                    op.getItens().add(new InspecaoItem(op, p.getNumero(), p.getTexto(), p.getEscala()));
                }
            }
            d.getOps().add(op);
            registrar(d, op, OP_INCLUIDA, null, null, limpar(req.numeroOp()), eu, dispositivo);
        } else if (op.isExcluida()) {
            throw new NegocioException(HttpStatus.GONE, "OP_EXCLUIDA", "Esta OP foi excluída da inspeção.");
        }
        // A fila do aparelho envia em ordem e sempre o estado mais novo da OP: vale o último envio.
        // (Comparar com o relógio do aparelho descartaria edições válidas em silêncio.)
        aplicar(d, op, req, eu, dispositivo);
        op.setAlteradoEmCliente(req.alteradoEmCliente() != null ? req.alteradoEmCliente() : Instant.now());
        d.tocar();
        dias.flush();
        return op(op, anexosPorItem(d));
    }

    private void aplicar(InspecaoDia d, InspecaoOp op, OpReq req, UsuarioLogado eu, String disp) {
        op.setOrdem(req.ordem());
        campo(d, op, "Nº OP", op.getNumeroOp(), limpar(req.numeroOp()), eu, disp);
        op.setNumeroOp(limpar(req.numeroOp()));
        campo(d, op, "Código do produto", op.getCodigoProduto(), limpar(req.codigoProduto()), eu, disp);
        op.setCodigoProduto(limpar(req.codigoProduto()));
        Map<Integer, InspecaoItem> porNumero = op.getItens().stream()
                .collect(Collectors.toMap(InspecaoItem::getNumero, i -> i));
        for (ItemReq ir : req.itens() == null ? List.<ItemReq>of() : req.itens()) {
            InspecaoItem i = porNumero.get(ir.numero());
            if (i == null) {
                throw NegocioException.invalido("Tópico " + ir.numero() + " não existe nesta OP.");
            }
            String valor = limpar(ir.valor());
            if (!InspecaoValores.aceita(i.getEscala(), valor)) {
                throw NegocioException.invalido("Resposta não prevista para \"" + i.getTopico() + "\": " + valor + ".");
            }
            campo(d, op, i.getTopico(), sigla(i, i.getValor()), sigla(i, valor), eu, disp);
            i.setValor(valor);
            String obs = limpar(ir.observacao());
            campo(d, op, i.getTopico() + " (observação)", i.getObservacao(), obs, eu, disp);
            i.setObservacao(obs);
        }
        if (req.lotes() != null) {
            Map<String, InspecaoLote> atuais = new LinkedHashMap<>();
            op.getLotes().forEach(l -> atuais.put(l.getId(), l));
            Set<String> enviados = new HashSet<>();
            for (LoteReq lr : req.lotes()) {
                enviados.add(lr.id());
                InspecaoLote l = atuais.get(lr.id());
                String texto = descreverLote(limpar(lr.lote()), limpar(lr.dataFabricacao()));
                if (l == null) {
                    l = new InspecaoLote(lr.id(), op);
                    op.getLotes().add(l);
                    registrar(d, op, LOTE_INCLUIDO, "Lote do insumo", null, texto, eu, disp);
                } else {
                    campo(d, op, "Lote do insumo", descreverLote(l.getLote(), l.getDataFabricacao()), texto, eu, disp);
                }
                l.setLote(limpar(lr.lote()));
                l.setDataFabricacao(limpar(lr.dataFabricacao()));
                l.setExcluido(false);
            }
            for (InspecaoLote l : atuais.values()) {
                if (!enviados.contains(l.getId()) && !l.isExcluido()) {
                    l.setExcluido(true);
                    registrar(d, op, LOTE_REMOVIDO, "Lote do insumo",
                            descreverLote(l.getLote(), l.getDataFabricacao()), null, eu, disp);
                }
            }
        }
    }

    @Transactional
    public void excluirOp(String diaId, String opId, UsuarioLogado eu, String dispositivo) {
        InspecaoDia d = buscar(diaId);
        exigirEditar(d, eu);
        InspecaoOp op = d.getOps().stream().filter(o -> o.getId().equals(opId)).findFirst().orElse(null);
        if (op == null || op.isExcluida()) {
            return; // já excluída ou nunca chegou ao servidor
        }
        op.excluir();
        d.tocar();
        registrar(d, op, OP_EXCLUIDA, null, op.getNumeroOp(), null, eu, dispositivo);
    }

    // ---------------------------------------------------------------- fotos e arquivos

    @Transactional
    public AnexoDto incluirAnexo(String diaId, String opId, int numero, UUID id, MultipartFile arquivo,
            UsuarioLogado eu, String dispositivo) {
        validarId(diaId);
        validarId(opId);
        var existente = anexos.findById(id.toString());
        if (existente.isPresent()) {
            // Reenvio (rede caiu depois do upload): devolve o que existe, mas só para quem vê o formulário.
            permissoes.exigirVer(eu, existente.get().getItem().getOp().getDia().getTemplate().getId());
            return AnexoDto.de(existente.get());
        }
        InspecaoDia d = buscar(diaId);
        exigirEditar(d, eu);
        InspecaoOp op = d.getOpsAtivas().stream().filter(o -> o.getId().equals(opId)).findFirst()
                .orElseThrow(() -> NegocioException.naoEncontrado("OP"));
        InspecaoItem item = op.getItens().stream().filter(i -> i.getNumero() == numero).findFirst()
                .orElseThrow(() -> NegocioException.naoEncontrado("Tópico"));
        var g = arquivos.gravar("inspecoes/" + diaId, id, arquivo);
        AnexoInspecao a = anexos.save(new AnexoInspecao(id.toString(), item, g.nome(), g.contentType(), g.tamanho(),
                g.sha256(), g.caminho(), eu.username()));
        registrar(d, op, ANEXO_INCLUIDO, item.getTopico(), null, g.nome() + " (sha256 " + g.sha256() + ")", eu,
                dispositivo);
        d.tocar();
        return AnexoDto.de(a);
    }

    @Transactional
    public void excluirAnexo(String id, UsuarioLogado eu, String dispositivo) {
        validarId(id);
        // Foto excluída no aparelho antes (ou durante) o envio: pode não existir aqui; não é erro.
        AnexoInspecao a = anexos.findById(id).orElse(null);
        if (a == null || a.isExcluido()) {
            return;
        }
        InspecaoDia d = a.getItem().getOp().getDia();
        exigirEditar(d, eu);
        a.excluir(eu.username());
        registrar(d, a.getItem().getOp(), ANEXO_EXCLUIDO, a.getItem().getTopico(), a.getNomeOriginal(), null, eu,
                dispositivo);
        d.tocar();
    }

    public record Arquivo(AnexoInspecao anexo, java.nio.file.Path caminho) {
    }

    @Transactional(readOnly = true)
    public Arquivo abrirAnexo(String id, UsuarioLogado eu) {
        validarId(id);
        AnexoInspecao a = anexos.findById(id).orElseThrow(() -> NegocioException.naoEncontrado("Anexo"));
        permissoes.exigirVer(eu, a.getItem().getOp().getDia().getTemplate().getId());
        return new Arquivo(a, arquivos.resolver(a.getCaminho()));
    }

    // ---------------------------------------------------------------- para o relatório

    @Transactional(readOnly = true)
    public InspecaoDia paraRelatorio(String id, UsuarioLogado eu) {
        InspecaoDia d = buscar(id);
        permissoes.exigirVer(eu, d.getTemplate().getId());
        d.getOpsAtivas().forEach(o -> {
            o.getItens().size();
            o.getLotes().size();
        });
        d.getInspetor().getNome();
        d.getTemplate().getRodape();
        return d;
    }

    @Transactional(readOnly = true)
    public List<AnexoInspecao> anexosAtivos(String diaId) {
        List<AnexoInspecao> l = anexos.ativosDoDia(diaId);
        l.forEach(a -> a.getItem().getOp().getId());
        return l;
    }

    public java.nio.file.Path caminho(AnexoInspecao a) {
        return arquivos.resolver(a.getCaminho());
    }

    // ---------------------------------------------------------------- auxiliares

    private void exigirEditar(InspecaoDia d, UsuarioLogado eu) {
        permissoes.exigirPreencher(eu, d.getTemplate().getId());
        if (!eu.isAdmin() && !d.getInspetor().getId().equals(eu.id())) {
            throw NegocioException.proibido("Só quem abriu a inspeção (ou um administrador) pode alterá-la.");
        }
        if (d.getStatus() == StatusInspecao.FINALIZADA) {
            throw new NegocioException(HttpStatus.CONFLICT, "FINALIZADA",
                    "Inspeção finalizada; peça a um administrador para reabrir.");
        }
    }

    private InspecaoDia buscar(String id) {
        validarId(id);
        return dias.findById(id).filter(d -> !d.isExcluida()).orElseThrow(() -> NegocioException.naoEncontrado("Inspeção"));
    }

    private Map<Long, List<AnexoDto>> anexosPorItem(InspecaoDia d) {
        Map<Long, List<AnexoDto>> m = new HashMap<>();
        for (AnexoInspecao a : anexos.ativosDoDia(d.getId())) {
            m.computeIfAbsent(a.getItem().getId(), k -> new ArrayList<>()).add(AnexoDto.de(a));
        }
        return m;
    }

    private DiaDetalhe detalhe(InspecaoDia d) {
        Map<Long, List<AnexoDto>> porItem = anexosPorItem(d);
        List<TopicoDto> topicos = d.getTemplate().getPerguntas().stream()
                .map(p -> new TopicoDto(p.getNumero(), p.getTexto(), p.getEscala())).toList();
        TemplateChecklist t = d.getTemplate();
        return new DiaDetalhe(d.getId(), t.getId(), t.getIdentificacao(), t.getRodape(), d.getData(),
                inspetor(d), d.getStatus(), d.getFinalizadaEm(), d.getCriadoEm(), d.getAtualizadoEm(), topicos,
                d.getOpsAtivas().stream().map(o -> op(o, porItem)).toList());
    }

    private static OpDto op(InspecaoOp o, Map<Long, List<AnexoDto>> porItem) {
        List<ItemDto> itens = o.getItens().stream()
                .map(i -> new ItemDto(i.getNumero(), i.getTopico(), i.getEscala(), i.getValor(), i.getObservacao(),
                        porItem.getOrDefault(i.getId(), List.of())))
                .toList();
        List<LoteDto> lotes = o.getLotesAtivos().stream()
                .map(l -> new LoteDto(l.getId(), l.getLote(), l.getDataFabricacao())).toList();
        return new OpDto(o.getId(), o.getOrdem(), o.getNumeroOp(), o.getCodigoProduto(), o.getAlteradoEmCliente(),
                itens, lotes);
    }

    private DiaResumo resumo(InspecaoDia d) {
        Map<String, Long> contagem = new LinkedHashMap<>();
        long nc = 0;
        for (InspecaoOp o : d.getOpsAtivas()) {
            for (InspecaoItem i : o.getItens()) {
                if (i.getValor() != null) {
                    contagem.merge(i.getValor(), 1L, Long::sum);
                    if (InspecaoValores.naoConforme(i.getEscala(), i.getValor())) {
                        nc++;
                    }
                }
            }
        }
        return new DiaResumo(d.getId(), d.getTemplate().getId(), d.getData(), inspetor(d), d.getStatus(),
                d.getOpsAtivas().size(), contagem, nc, d.getAtualizadoEm());
    }

    private static InspetorDto inspetor(InspecaoDia d) {
        var u = d.getInspetor();
        return new InspetorDto(u.getId(), u.getNome(), u.getUsername());
    }

    private void campo(InspecaoDia d, InspecaoOp op, String campo, String antes, String depois, UsuarioLogado eu,
            String disp) {
        if (!Objects.equals(antes, depois)) {
            registrar(d, op, OP_ALTERADA, campo, antes, depois, eu, disp);
        }
    }

    private void registrar(InspecaoDia d, InspecaoOp op, String evento, String campo, String antes, String depois,
            UsuarioLogado eu, String disp) {
        registros.save(new RegistroInspecao(d.getId(), op == null ? null : op.getId(),
                op == null ? null : op.getNumeroOp(), evento, campo, antes, depois, eu.username(), disp));
    }

    private static String sigla(InspecaoItem i, String valor) {
        return valor == null ? null : InspecaoValores.sigla(i.getEscala(), valor);
    }

    private static String descreverLote(String lote, String data) {
        if (lote == null && data == null) {
            return null;
        }
        return (lote == null ? "(sem lote)" : lote) + (data == null ? "" : " · fab. " + data);
    }

    private static void validarId(String id) {
        if (id == null || !id.matches("[0-9a-fA-F-]{8,36}")) {
            throw NegocioException.invalido("Identificador inválido.");
        }
    }

    static String limpar(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
