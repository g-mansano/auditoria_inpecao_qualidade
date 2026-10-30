package dev.mansano.auditoria.historico;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroAlteracaoRepository extends JpaRepository<RegistroAlteracao, Long> {

    List<RegistroAlteracao> findByAuditoriaIdOrderByRegistradoEmDescIdDesc(Long auditoriaId);
}
