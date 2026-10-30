package dev.mansano.auditoria.usuario;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TokenAcessoRepository extends JpaRepository<TokenAcesso, String> {

    @Modifying
    @Query("delete from TokenAcesso t where t.expiraEm < :agora")
    int apagarExpirados(Instant agora);

    @Modifying
    @Query("delete from TokenAcesso t where t.usuario.id = :usuarioId")
    int apagarDoUsuario(Long usuarioId);
}
