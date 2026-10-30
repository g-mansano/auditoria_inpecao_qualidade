package dev.mansano.auditoria.inspecao;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InspecaoDiaRepository extends JpaRepository<InspecaoDia, String> {

    Optional<InspecaoDia> findByTemplateIdAndInspetorIdAndDataAndExcluidaFalse(Long templateId, Long inspetorId, LocalDate data);

    List<InspecaoDia> findByTemplateIdInAndExcluidaFalseOrderByDataDescCriadoEmDesc(Collection<Long> templateIds);

    /** [templateId, status, quantidade] para os cartões da tela inicial. */
    @Query("select d.template.id, d.status, count(d) from InspecaoDia d where d.excluida = false group by d.template.id, d.status")
    List<Object[]> contarPorStatus();
}
