package dev.mansano.auditoria.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.mansano.auditoria.config.AppProperties;
import dev.mansano.auditoria.usuario.TokenAcesso;
import dev.mansano.auditoria.usuario.TokenAcessoRepository;
import dev.mansano.auditoria.usuario.Usuario;

/**
 * Tokens opacos de longa duração. O inspetor pode ficar dias sem rede na fábrica,
 * então o token precisa sobreviver a isso; o banco guarda só o hash.
 */
@Service
public class TokenService {

    private final SecureRandom random = new SecureRandom();
    private final TokenAcessoRepository repo;
    private final AppProperties props;

    public TokenService(TokenAcessoRepository repo, AppProperties props) {
        this.repo = repo;
        this.props = props;
    }

    public record TokenEmitido(String token, Instant expiraEm) {
    }

    @Transactional
    public TokenEmitido emitir(Usuario usuario) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant agora = Instant.now();
        Instant expira = agora.plus(Duration.ofDays(props.tokenValidadeDias()));
        repo.save(new TokenAcesso(hash(token), usuario, agora, expira));
        return new TokenEmitido(token, expira);
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> validar(String token) {
        return repo.findById(hash(token))
                .filter(t -> t.getExpiraEm().isAfter(Instant.now()))
                .map(TokenAcesso::getUsuario)
                .filter(Usuario::isAtivo);
    }

    @Transactional
    public void revogar(String token) {
        repo.deleteById(hash(token));
    }

    @Transactional
    public void revogarTodos(Long usuarioId) {
        repo.apagarDoUsuario(usuarioId);
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void limparExpirados() {
        repo.apagarExpirados(Instant.now());
    }

    static String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
