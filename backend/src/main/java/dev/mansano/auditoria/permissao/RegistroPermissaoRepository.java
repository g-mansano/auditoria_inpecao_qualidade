package dev.mansano.auditoria.permissao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroPermissaoRepository extends JpaRepository<RegistroPermissao, Long> {

    List<RegistroPermissao> findByUsuarioIdOrderByAlteradoEmDescIdDesc(Long usuarioId);
}
