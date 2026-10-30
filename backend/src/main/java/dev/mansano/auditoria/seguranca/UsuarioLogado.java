package dev.mansano.auditoria.seguranca;

import dev.mansano.auditoria.usuario.Papel;
import dev.mansano.auditoria.usuario.Usuario;

/** Principal colocado no SecurityContext pelo {@link TokenAuthFilter}. */
public record UsuarioLogado(Long id, String username, String nome, Papel papel) {

    public static UsuarioLogado de(Usuario u) {
        return new UsuarioLogado(u.getId(), u.getUsername(), u.getNome(), u.getPapel());
    }

    public boolean isAdmin() {
        return papel == Papel.ADMIN;
    }
}
