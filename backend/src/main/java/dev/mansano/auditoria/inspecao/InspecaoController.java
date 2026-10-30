package dev.mansano.auditoria.inspecao;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

import dev.mansano.auditoria.inspecao.InspecaoDtos.*;
import dev.mansano.auditoria.seguranca.UsuarioLogado;
import jakarta.validation.Valid;

/** Inspeção de OPs (FQ-106). Escrita exige o header X-Dispositivo, como nas auditorias. */
@RestController
@RequestMapping("/api/inspecoes")
public class InspecaoController {

    private static final String DISPOSITIVO = "X-Dispositivo";

    private final InspecaoService service;
    private final RelatorioInspecaoService relatorios;

    public InspecaoController(InspecaoService service, RelatorioInspecaoService relatorios) {
        this.service = service;
        this.relatorios = relatorios;
    }

    @GetMapping
    public List<DiaResumo> listar(@AuthenticationPrincipal UsuarioLogado eu) {
        return service.listar(eu);
    }

    @GetMapping("/{id}")
    public DiaDetalhe detalhe(@PathVariable String id, @AuthenticationPrincipal UsuarioLogado eu) {
        return service.detalhe(id, eu);
    }

    /** Abre a inspeção do dia com o id gerado no aparelho (reenvio devolve a mesma). */
    @PutMapping("/{id}")
    public DiaDetalhe abrir(@PathVariable String id, @Valid @RequestBody NovoDiaReq req,
            @AuthenticationPrincipal UsuarioLogado eu, @RequestHeader(DISPOSITIVO) String dispositivo) {
        return service.abrirDia(id, req, eu, dispositivo);
    }

    /** Admin exclui (logicamente) uma inspeção, ex.: as de teste. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable String id, @AuthenticationPrincipal UsuarioLogado eu,
            @RequestHeader(DISPOSITIVO) String dispositivo) {
        service.excluir(id, eu, dispositivo);
    }

    @PostMapping("/{id}/finalizar")
    public DiaDetalhe finalizar(@PathVariable String id, @AuthenticationPrincipal UsuarioLogado eu,
            @RequestHeader(DISPOSITIVO) String dispositivo) {
        return service.finalizar(id, eu, dispositivo);
    }

    @PostMapping("/{id}/reabrir")
    public DiaDetalhe reabrir(@PathVariable String id, @AuthenticationPrincipal UsuarioLogado eu,
            @RequestHeader(DISPOSITIVO) String dispositivo) {
        return service.reabrir(id, eu, dispositivo);
    }

    @GetMapping("/{id}/historico")
    public List<HistoricoDto> historico(@PathVariable String id, @AuthenticationPrincipal UsuarioLogado eu) {
        return service.historico(id, eu);
    }

    @PutMapping("/{id}/ops/{opId}")
    public OpDto salvarOp(@PathVariable String id, @PathVariable String opId, @Valid @RequestBody OpReq req,
            @AuthenticationPrincipal UsuarioLogado eu, @RequestHeader(DISPOSITIVO) String dispositivo) {
        return service.salvarOp(id, opId, req, eu, dispositivo);
    }

    @DeleteMapping("/{id}/ops/{opId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirOp(@PathVariable String id, @PathVariable String opId,
            @AuthenticationPrincipal UsuarioLogado eu, @RequestHeader(DISPOSITIVO) String dispositivo) {
        service.excluirOp(id, opId, eu, dispositivo);
    }

    @PostMapping(path = "/{id}/ops/{opId}/itens/{numero}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AnexoDto incluirAnexo(@PathVariable String id, @PathVariable String opId, @PathVariable int numero,
            @RequestParam UUID anexoId, @RequestParam("arquivo") MultipartFile arquivo,
            @AuthenticationPrincipal UsuarioLogado eu, @RequestHeader(DISPOSITIVO) String dispositivo) {
        return service.incluirAnexo(id, opId, numero, anexoId, arquivo, eu, dispositivo);
    }

    @DeleteMapping("/anexos/{anexoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirAnexo(@PathVariable String anexoId, @AuthenticationPrincipal UsuarioLogado eu,
            @RequestHeader(DISPOSITIVO) String dispositivo) {
        service.excluirAnexo(anexoId, eu, dispositivo);
    }

    @GetMapping("/anexos/{anexoId}")
    public ResponseEntity<Resource> baixarAnexo(@PathVariable String anexoId,
            @AuthenticationPrincipal UsuarioLogado eu) {
        var a = service.abrirAnexo(anexoId, eu);
        String tipo = a.anexo().getContentType();
        boolean inline = tipo.startsWith("image/") || tipo.equals("application/pdf");
        var disp = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(a.anexo().getNomeOriginal(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(tipo))
                .header(HttpHeaders.CONTENT_DISPOSITION, disp.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable")
                .body(new FileSystemResource(a.caminho()));
    }

    /** PDF do dia para o PCP. */
    @GetMapping("/{id}/relatorio.pdf")
    public ResponseEntity<byte[]> relatorio(@PathVariable String id, @AuthenticationPrincipal UsuarioLogado eu) {
        var r = relatorios.gerar(id, eu);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(r.nomeArquivo(), StandardCharsets.UTF_8).build()
                                .toString())
                .body(r.pdf());
    }
}
