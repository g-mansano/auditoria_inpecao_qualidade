package dev.mansano.auditoria.template;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RevisaoFormularioDetalheRepository extends JpaRepository<RevisaoFormularioDetalhe, Long> {

    List<RevisaoFormularioDetalhe> findByRevisaoIdInOrderByIdAsc(Collection<Long> revisaoIds);
}
