package dev.mansano.auditoria.auditoria;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    @EntityGraph(attributePaths = { "editor", "itens", "template" })
    List<Auditoria> findByExcluidaFalseOrderByDataInicioDescIdDesc();

    /** Auditorias que recebem uma revisão salva: em andamento, nunca concluídas, não excluídas. */
    @EntityGraph(attributePaths = { "itens" })
    List<Auditoria> findByTemplateIdAndStatusAndRevisaoCongeladaFalseAndExcluidaFalse(Long templateId,
            StatusAuditoria status);
}
