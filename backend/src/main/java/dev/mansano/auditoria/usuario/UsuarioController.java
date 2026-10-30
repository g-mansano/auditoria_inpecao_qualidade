package dev.mansano.auditoria.usuario;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.mansano.auditoria.comum.NegocioException;
import dev.mansano.auditoria.demo.ProtecaoDemo;
import dev.mansano.auditoria.seguranca.LoginRateLimiter;
import dev.mansano.auditoria.seguranca.TokenService;
import dev.mansano.auditoria.seguranca.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api")
public class UsuarioController {

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final LoginRateLimiter rateLimiter;
    private final ProtecaoDemo protecaoDemo;

    public UsuarioController(UsuarioRepository repo, PasswordEncoder encoder, TokenService tokens,
            LoginRateLimiter rateLimiter, ProtecaoDemo protecaoDemo) {
        this.repo = repo;
        this.encoder = encoder;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
        this.protecaoDemo = protecaoDemo;
    }

    /** {@code podeRevisar}: na sessão, a permissão efetiva (admin sempre pode); na administração, o valor gravado. */
    public record UsuarioDto(Long id, String username, String nome, Papel papel, boolean ativo, boolean podeRevisar) {
        static UsuarioDto de(Usuario u) {
            return new UsuarioDto(u.getId(), u.getUsername(), u.getNome(), u.getPapel(), u.isAtivo(),
                    u.isPodeRevisarMarcado());
        }

        static UsuarioDto daSessao(Usuario u) {
            return new UsuarioDto(u.getId(), u.getUsername(), u.getNome(), u.getPapel(), u.isAtivo(),
                    u.isPodeRevisar());
        }
    }

    public record LoginReq(@NotBlank String username, @NotBlank String senha) {
    }

    public record LoginResp(String token, Instant expiraEm, UsuarioDto usuario) {
    }

    public record NovoUsuarioReq(
            @NotBlank @Size(max = 60) String username,
            @NotBlank @Size(max = 150) String nome,
            @NotBlank @Size(min = 8, max = 100) String senha,
            @NotNull Papel papel) {
    }

    public record AlterarUsuarioReq(
            @NotBlank @Size(max = 150) String nome,
            @NotNull Papel papel,
            boolean ativo,
            @Size(min = 8, max = 100) String novaSenha,
            boolean podeRevisar) {
    }

    public record TrocarSenhaReq(@NotBlank String senhaAtual, @NotBlank @Size(min = 8, max = 100) String novaSenha) {
    }

    // ---- autenticação ----

    @PostMapping("/auth/login")
    @Transactional
    public LoginResp login(@Valid @RequestBody LoginReq req) {
        String username = req.username().trim();
        rateLimiter.verificarBloqueio(username);

        Usuario u = repo.findByUsernameIgnoreCase(username)
                .filter(Usuario::isAtivo)
                .filter(x -> encoder.matches(req.senha(), x.getSenhaHash()))
                .orElse(null);

        if (u == null) {
            rateLimiter.registrarFalha(username);
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "LOGIN_INVALIDO",
                    "Usuário ou senha inválidos.");
        }

        rateLimiter.registrarSucesso(username);
        var t = tokens.emitir(u);
        return new LoginResp(t.token(), t.expiraEm(), UsuarioDto.daSessao(u));
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String header) {
        if (header != null && header.startsWith("Bearer ") && header.length() > 7) {
            tokens.revogar(header.substring(7).trim());
        }
    }

    @GetMapping("/auth/eu")
    public UsuarioDto eu(@AuthenticationPrincipal UsuarioLogado eu) {
        return UsuarioDto.daSessao(repo.findById(eu.id()).orElseThrow());
    }

    @PostMapping("/auth/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void trocarSenha(@AuthenticationPrincipal UsuarioLogado eu, @Valid @RequestBody TrocarSenhaReq req) {
        protecaoDemo.exigirAlteravel(eu.id());
        Usuario u = repo.findById(eu.id()).orElseThrow();
        if (!encoder.matches(req.senhaAtual(), u.getSenhaHash())) {
            throw NegocioException.invalido("Senha atual incorreta.");
        }
        u.setSenhaHash(encoder.encode(req.novaSenha()));
        tokens.revogarTodos(u.getId());
    }

    // ---- administração ----

    @GetMapping("/usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UsuarioDto> listar() {
        return repo.findAllByOrderByNomeAsc().stream().map(UsuarioDto::de).toList();
    }

    @PostMapping("/usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UsuarioDto criar(@Valid @RequestBody NovoUsuarioReq req) {
        String username = req.username().trim().toLowerCase();
        if (repo.existsByUsernameIgnoreCase(username)) {
            throw NegocioException.invalido("Já existe um usuário com esse login.");
        }
        Usuario u = repo.save(new Usuario(username, req.nome().trim(), encoder.encode(req.senha()), req.papel()));
        return UsuarioDto.de(u);
    }

    @PutMapping("/usuarios/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UsuarioDto alterar(@PathVariable Long id, @Valid @RequestBody AlterarUsuarioReq req,
            @AuthenticationPrincipal UsuarioLogado eu) {
        protecaoDemo.exigirAlteravel(id);
        Usuario u = repo.findById(id).orElseThrow(() -> NegocioException.naoEncontrado("Usuário"));
        if (u.getId().equals(eu.id()) && (!req.ativo() || req.papel() != Papel.ADMIN)) {
            throw NegocioException.invalido("Você não pode remover o próprio acesso de administrador.");
        }
        boolean papelMudou = u.getPapel() != req.papel();
        boolean senhaMudou = req.novaSenha() != null && !req.novaSenha().isBlank();
        u.setNome(req.nome().trim());
        u.setPapel(req.papel());
        u.setAtivo(req.ativo());
        // Admin revisa por ser admin; gravar a chave faria um admin rebaixado continuar revisando.
        u.setPodeRevisar(req.papel() != Papel.ADMIN && req.podeRevisar());
        if (senhaMudou) {
            u.setSenhaHash(encoder.encode(req.novaSenha()));
        }
        if (!req.ativo() || senhaMudou || papelMudou) {
            tokens.revogarTodos(u.getId());
        }
        return UsuarioDto.de(u);
    }
}
