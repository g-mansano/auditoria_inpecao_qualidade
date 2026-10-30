package dev.mansano.auditoria.inspecao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnexoInspecaoRepository extends JpaRepository<AnexoInspecao, String> {

    @Query("select a from AnexoInspecao a where a.item.op.dia.id = :diaId and a.excluido = false order by a.enviadoEm")
    List<AnexoInspecao> ativosDoDia(@Param("diaId") String diaId);
}
