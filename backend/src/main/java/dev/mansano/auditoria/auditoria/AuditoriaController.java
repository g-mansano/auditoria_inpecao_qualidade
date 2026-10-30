package dev.mansano.auditoria.auditoria;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.mansano.auditoria.auditoria.AuditoriaDtos.*;
import dev.mansano.auditoria.seguranca.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Toda escrita exige o header {@code X-Dispositivo}: id gerado e guardado no aparelho,
 * usado para garantir que só um aparelho edita a auditoria por vez.
 */
@RestController
@RequestMapping("/api/auditorias")
public class AuditoriaController {

    private static final String DISPOSITIVO = "X-Dispositivo";

    private final AuditoriaService service;
    public AuditoriaController(AuditoriaService service) {
        this.service = service;
    }

    @GetMapping
    public List<AuditoriaResumo> listar(@AuthenticationPrincipal UsuarioLogado eu) {
        return service.listar(eu);
    }

    @GetMapping("/{id}")
    public AuditoriaDetalhe detalhe(@PathVariable Long id, @AuthenticationPrincipal UsuarioLogado eu) {
        return service.detalhe(id, eu);
    }

    @GetMapping("/{id}/historico")
    public List<HistoricoDto> historico(@PathVariable Long id, @AuthenticationPrincipal UsuarioLogado eu) {
        return service.historico(id, eu);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuditoriaDetalhe criar(@Valid @RequestBody NovaAuditoriaReq req, @AuthenticationPrincipal UsuarioLogado eu,
            @RequestHeader(DISPOSITIVO) @NotBlank @Size(max = 64) String dispositivo) {
        return service.criar(req, eu, dispositivo);
    }

    @PutMapping("/{id}")
    public AuditoriaDetalhe cabecalho(@PathVariable Long id, @Valid @RequestBody CabecalhoReq req,
            @AuthenticationPrincipal UsuarioLogado eu, @RequestHeader(DISPOSITIVO) String dispositivo) {
        return service.atualizarCabecalho(id, req, eu, dispositivo);
    }

    @PutMapping("/{id}/itens/{itemId}")
    public ItemDto item(@PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody ItemReq req,
            @AuthenticationPrincipal UsuarioLogado eu, @RequestHeader(DISPOSITIVO) String dispositivo) {
        return service.atualizarItem(id, itemId, req, eu, dispositivo);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id, @AuthenticationPrincipal UsuarioLogado eu,
            @RequestHeader(DISPOSITIVO) String dispositivo) {
        service.excluir(id, eu, dispositivo);
    }

    @PostMapping("/{id}/checkout")
    public AuditoriaDetalhe checkout(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean forcar,
            @AuthenticationPrincipal UsuarioLogado eu,
            @RequestHeader(DISPOSITIVO) @NotBlank @Size(max = 64) String dispositivo) {
        return service.checkout(id, eu, dispositivo, forcar);
    }

    @PostMapping("/{id}/checkin")
    public AuditoriaDetalhe checkin(@PathVariable Long id, @AuthenticationPrincipal UsuarioLogado eu,
            @RequestHeader(DISPOSITIVO) String dispositivo) {
        return service.checkin(id, eu, dispositivo);
    }

    @PostMapping("/{id}/concluir")
    public AuditoriaDetalhe concluir(@PathVariable Long id, @AuthenticationPrincipal UsuarioLogado eu,
            @RequestHeader(DISPOSITIVO) String dispositivo) {
        return service.concluir(id, eu, dispositivo);
    }

    @PostMapping("/{id}/reabrir")
    public AuditoriaDetalhe reabrir(@PathVariable Long id, @AuthenticationPrincipal UsuarioLogado eu,
            @RequestHeader(DISPOSITIVO) String dispositivo) {
        return service.reabrir(id, eu, dispositivo);
    }

    /** O inspetor viu a janela de perguntas removidas por uma revisão. */
    @PostMapping("/{id}/removidos/vistos")
    public AuditoriaDetalhe removidosVistos(@PathVariable Long id, @AuthenticationPrincipal UsuarioLogado eu,
            @RequestHeader(DISPOSITIVO) String dispositivo) {
        return service.marcarRemovidosVistos(id, eu, dispositivo);
    }
}
