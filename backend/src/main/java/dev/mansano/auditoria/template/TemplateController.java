package dev.mansano.auditoria.template;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mansano.auditoria.auditoria.AuditoriaDtos.FormularioDto;
import dev.mansano.auditoria.auditoria.AuditoriaRepository;
import dev.mansano.auditoria.auditoria.StatusAuditoria;
import dev.mansano.auditoria.inspecao.InspecaoDiaRepository;
import dev.mansano.auditoria.inspecao.StatusInspecao;
import dev.mansano.auditoria.permissao.NivelAcesso;
import dev.mansano.auditoria.permissao.PermissaoService;
import dev.mansano.auditoria.seguranca.UsuarioLogado;
import dev.mansano.auditoria.template.RevisaoDtos.ResultadoRevisao;
import dev.mansano.auditoria.template.RevisaoDtos.RevisaoDto;
import dev.mansano.auditoria.template.RevisaoDtos.SalvarRevisaoReq;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateRepository repo;
    private final AuditoriaRepository auditorias;
    private final RevisaoService revisoes;
    private final PermissaoService permissoes;
    private final InspecaoDiaRepository inspecoes;

    public TemplateController(TemplateRepository repo, AuditoriaRepository auditorias, RevisaoService revisoes,
            PermissaoService permissoes, InspecaoDiaRepository inspecoes) {
        this.repo = repo;
        this.auditorias = auditorias;
        this.revisoes = revisoes;
        this.permissoes = permissoes;
        this.inspecoes = inspecoes;
    }

    // ---- revisão do formulário (quem tem a permissão "pode revisar", e o admin) ----

    @GetMapping("/{id}/revisao")
    public RevisaoDto revisao(@PathVariable Long id, @AuthenticationPrincipal UsuarioLogado eu) {
        return revisoes.carregar(id, eu);
    }

    @PostMapping("/{id}/revisao/previa")
    public ResultadoRevisao previa(@PathVariable Long id, @Valid @RequestBody SalvarRevisaoReq req,
            @AuthenticationPrincipal UsuarioLogado eu) {
        return revisoes.previa(id, req, eu);
    }

    @PutMapping("/{id}/revisao")
    public ResultadoRevisao salvar(@PathVariable Long id, @Valid @RequestBody SalvarRevisaoReq req,
            @AuthenticationPrincipal UsuarioLogado eu) {
        return revisoes.salvar(id, req, eu);
    }

    /**
     * Formulário com a contagem de auditorias (ou de inspeções, no FQ-106) e o nível de acesso do usuário,
     * para a tela inicial. Só aparecem os formulários liberados para ele.
     */
    public record TemplateDto(FormularioDto formulario, int totalPerguntas, long emAndamento, long concluidas,
            NivelAcesso nivel) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<TemplateDto> ativos(@AuthenticationPrincipal UsuarioLogado eu) {
        Map<Long, NivelAcesso> acesso = permissoes.mapa(eu);
        Map<Long, Map<String, Long>> contagem = new HashMap<>();
        auditorias.findByExcluidaFalseOrderByDataInicioDescIdDesc().forEach(a -> contagem
                .computeIfAbsent(a.getTemplate().getId(), k -> new HashMap<>())
                .merge(a.getStatus() == StatusAuditoria.CONCLUIDA ? "FIM" : "ANDAMENTO", 1L, Long::sum));
        for (Object[] r : inspecoes.contarPorStatus()) {
            contagem.computeIfAbsent((Long) r[0], k -> new HashMap<>())
                    .merge(r[1] == StatusInspecao.FINALIZADA ? "FIM" : "ANDAMENTO", (Long) r[2], Long::sum);
        }
        return repo.findByAtivoTrueOrderByOrdemAscCodigoAsc().stream()
                .filter(t -> acesso.containsKey(t.getId()))
                .map(t -> {
                    var c = contagem.getOrDefault(t.getId(), Map.of());
                    return new TemplateDto(FormularioDto.de(t), t.getPerguntas().size(),
                            c.getOrDefault("ANDAMENTO", 0L), c.getOrDefault("FIM", 0L), acesso.get(t.getId()));
                })
                .toList();
    }

    /** Histórico de revisões do formulário (quem salvou, quando, e o que mudou em cada pergunta). */
    @GetMapping("/{id}/revisoes")
    public List<RevisaoDtos.HistoricoRevisaoDto> historicoRevisoes(@PathVariable Long id,
            @AuthenticationPrincipal UsuarioLogado eu) {
        permissoes.exigirVer(eu, id);
        return revisoes.historico(id);
    }
}
