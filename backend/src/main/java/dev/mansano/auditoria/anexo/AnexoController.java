package dev.mansano.auditoria.anexo;

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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.mansano.auditoria.auditoria.AuditoriaDtos.AnexoDto;
import dev.mansano.auditoria.seguranca.UsuarioLogado;

@RestController
@RequestMapping("/api")
public class AnexoController {

    private static final String DISPOSITIVO = "X-Dispositivo";

    private final AnexoService service;

    public AnexoController(AnexoService service) {
        this.service = service;
    }

    @PostMapping(path = "/auditorias/{auditoriaId}/itens/{itemId}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AnexoDto incluir(@PathVariable Long auditoriaId, @PathVariable Long itemId, @RequestParam UUID id,
            @RequestParam("arquivo") MultipartFile arquivo, @AuthenticationPrincipal UsuarioLogado eu,
            @RequestHeader(DISPOSITIVO) String dispositivo) {
        return service.incluir(auditoriaId, itemId, id, arquivo, eu, dispositivo);
    }

    @DeleteMapping("/anexos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id, @AuthenticationPrincipal UsuarioLogado eu,
            @RequestHeader(DISPOSITIVO) String dispositivo) {
        service.excluir(id, eu, dispositivo);
    }

    @GetMapping("/anexos/{id}")
    public ResponseEntity<Resource> baixar(@PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean download, @AuthenticationPrincipal UsuarioLogado eu) {
        var c = service.abrir(id, eu);
        String tipo = c.anexo().getContentType();
        // Só imagem e PDF abrem no navegador; o resto sempre baixa.
        boolean inline = !download && (tipo.startsWith("image/") || tipo.equals("application/pdf"));
        var disp = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(c.anexo().getNomeOriginal(), java.nio.charset.StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tipo))
                .header(HttpHeaders.CONTENT_DISPOSITION, disp.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable")
                .body(new FileSystemResource(c.arquivo()));
    }
}
