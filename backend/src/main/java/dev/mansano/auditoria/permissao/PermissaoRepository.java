package dev.mansano.auditoria.permissao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermissaoRepository extends JpaRepository<PermissaoFormulario, PermissaoFormulario.Chave> {

    @Query("select p from PermissaoFormulario p where p.id.usuarioId = :usuarioId")
    List<PermissaoFormulario> doUsuario(@Param("usuarioId") Long usuarioId);
}
