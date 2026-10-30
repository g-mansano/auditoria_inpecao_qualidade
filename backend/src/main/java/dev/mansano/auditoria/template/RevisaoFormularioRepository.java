package dev.mansano.auditoria.template;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RevisaoFormularioRepository extends JpaRepository<RevisaoFormulario, Long> {

    Optional<RevisaoFormulario> findFirstByTemplateIdOrderBySalvoEmDescIdDesc(Long templateId);

    java.util.List<RevisaoFormulario> findByTemplateIdOrderBySalvoEmDescIdDesc(Long templateId);
}
