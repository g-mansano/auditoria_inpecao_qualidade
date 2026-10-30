package dev.mansano.auditoria.anexo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnexoRepository extends JpaRepository<Anexo, UUID> {

    @Query("select a from Anexo a join fetch a.item i where i.auditoria.id = :auditoriaId and a.excluido = false order by i.numero, a.enviadoEm")
    List<Anexo> ativosDaAuditoria(Long auditoriaId);
}
