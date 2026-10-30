package dev.mansano.auditoria.template;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface TemplateRepository extends JpaRepository<TemplateChecklist, Long> {

    List<TemplateChecklist> findByAtivoTrueOrderByOrdemAscCodigoAsc();

    /** Trava a linha (SELECT ... FOR UPDATE): dois salvamentos da mesma revisão não correm juntos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TemplateChecklist t where t.id = :id")
    Optional<TemplateChecklist> travar(@Param("id") Long id);
}
