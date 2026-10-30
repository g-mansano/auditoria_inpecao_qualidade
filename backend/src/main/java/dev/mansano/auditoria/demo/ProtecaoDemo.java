package dev.mansano.auditoria.demo;

import org.springframework.stereotype.Component;

import dev.mansano.auditoria.comum.NegocioException;
import dev.mansano.auditoria.config.AppProperties;
import dev.mansano.auditoria.usuario.UsuarioRepository;

/**
 * Na demonstração, as contas de exemplo são compartilhadas por todos os visitantes: ninguém troca a senha,
 * o papel ou as permissões delas (o resto do app funciona normalmente, inclusive criar usuários novos).
 */
@Component
public class ProtecaoDemo {

    private final AppProperties props;
    private final UsuarioRepository usuarios;

    public ProtecaoDemo(AppProperties props, UsuarioRepository usuarios) {
        this.props = props;
        this.usuarios = usuarios;
    }

    public void exigirAlteravel(Long usuarioId) {
        if (!props.demo().ativo()) {
            return;
        }
        usuarios.findById(usuarioId).ifPresent(u -> {
            if (ContasDemo.ehContaDemo(u.getUsername())) {
                throw NegocioException.proibido(
                        "Na demonstração, as contas de exemplo não podem ser alteradas. Crie um usuário novo para testar.");
            }
        });
    }
}
