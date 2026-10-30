package dev.mansano.auditoria.permissao;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Quem liberou ou retirou o acesso de quem a qual formulário. Só INSERT. */
@Entity
@Table(name = "registro_permissao")
public class RegistroPermissao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "template_id")
    private Long templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_anterior")
    private NivelAcesso nivelAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_novo")
    private NivelAcesso nivelNovo;

    @Column(name = "alterado_por")
    private String alteradoPor;

    @Column(name = "alterado_em")
    private Instant alteradoEm = Instant.now();

    protected RegistroPermissao() {
    }

    public RegistroPermissao(Long usuarioId, Long templateId, NivelAcesso antes, NivelAcesso depois, String por) {
        this.usuarioId = usuarioId;
        this.templateId = templateId;
        this.nivelAnterior = antes;
        this.nivelNovo = depois;
        this.alteradoPor = por;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public NivelAcesso getNivelAnterior() {
        return nivelAnterior;
    }

    public NivelAcesso getNivelNovo() {
        return nivelNovo;
    }

    public String getAlteradoPor() {
        return alteradoPor;
    }

    public Instant getAlteradoEm() {
        return alteradoEm;
    }
}
