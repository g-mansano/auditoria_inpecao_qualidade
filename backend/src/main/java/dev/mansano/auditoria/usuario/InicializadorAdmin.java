package dev.mansano.auditoria.usuario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.mansano.auditoria.config.AppProperties;

/**
 * Cria o primeiro administrador quando o banco não tem nenhum ADMIN
 * (criar só com a tabela vazia deixaria sem admin um banco que já nasce com outros usuários).
 * Roda antes dos dados de demonstração.
 */
@Component
@Order(0)
public class InicializadorAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorAdmin.class);

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public InicializadorAdmin(UsuarioRepository repo, PasswordEncoder encoder, AppProperties props) {
        this.repo = repo;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var a = props.adminInicial();
        if (repo.existsByPapel(Papel.ADMIN) || repo.existsByUsernameIgnoreCase(a.username())) {
            return;
        }
        repo.save(new Usuario(a.username(), a.nome(), encoder.encode(a.senha()), Papel.ADMIN));
        log.warn("Usuário administrador inicial '{}' criado. Troque a senha no primeiro acesso.", a.username());
    }
}
