package dev.mansano.auditoria.inspecao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroInspecaoRepository extends JpaRepository<RegistroInspecao, Long> {

    List<RegistroInspecao> findByDiaIdOrderByRegistradoEmDescIdDesc(String diaId);
}
