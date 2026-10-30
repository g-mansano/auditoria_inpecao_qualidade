package dev.mansano.auditoria.permissao;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mansano.auditoria.permissao.PermissaoService.PermissaoDto;
import dev.mansano.auditoria.permissao.PermissaoService.PermissaoReq;
import dev.mansano.auditoria.permissao.PermissaoService.RegistroPermissaoDto;
import dev.mansano.auditoria.demo.ProtecaoDemo;
import dev.mansano.auditoria.seguranca.UsuarioLogado;

/** Administração de quem vê e quem preenche cada formulário. */
@RestController
@RequestMapping("/api/usuarios/{usuarioId}/permissoes")
@PreAuthorize("hasRole('ADMIN')")
public class PermissaoController {

    private final PermissaoService service;
    private final ProtecaoDemo protecaoDemo;

    public PermissaoController(PermissaoService service, ProtecaoDemo protecaoDemo) {
        this.service = service;
        this.protecaoDemo = protecaoDemo;
    }

    @GetMapping
    public List<PermissaoDto> listar(@PathVariable Long usuarioId) {
        return service.doUsuario(usuarioId);
    }

    /** Lista completa ou parcial: cada item com nível nulo retira o acesso àquele formulário. */
    @PutMapping
    public List<PermissaoDto> definir(@PathVariable Long usuarioId, @RequestBody List<PermissaoReq> req,
            @AuthenticationPrincipal UsuarioLogado eu) {
        protecaoDemo.exigirAlteravel(usuarioId);
        return service.definir(usuarioId, req, eu);
    }

    @GetMapping("/historico")
    public List<RegistroPermissaoDto> historico(@PathVariable Long usuarioId) {
        return service.historico(usuarioId);
    }
}
