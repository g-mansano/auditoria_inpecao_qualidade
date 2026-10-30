package dev.mansano.auditoria.template;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Registro de um salvamento de revisão (quem, quando, o que mudou). Só INSERT. */
@Entity
@Table(name = "revisao_formulario")
public class RevisaoFormulario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id")
    private Long templateId;

    private String revisao;
    private boolean nova;
    private int alteradas;
    private int novas;
    private int removidas;
    private int auditorias;

    @Column(name = "salvo_por")
    private String salvoPor;

    @Column(name = "salvo_em")
    private Instant salvoEm = Instant.now();

    protected RevisaoFormulario() {
    }

    public RevisaoFormulario(Long templateId, String revisao, boolean nova, int alteradas, int novas, int removidas,
            int auditorias, String salvoPor) {
        this.templateId = templateId;
        this.revisao = revisao;
        this.nova = nova;
        this.alteradas = alteradas;
        this.novas = novas;
        this.removidas = removidas;
        this.auditorias = auditorias;
        this.salvoPor = salvoPor;
    }

    public Long getId() {
        return id;
    }

    public String getRevisao() {
        return revisao;
    }

    public boolean isNova() {
        return nova;
    }

    public int getAlteradas() {
        return alteradas;
    }

    public int getNovas() {
        return novas;
    }

    public int getRemovidas() {
        return removidas;
    }

    public int getAuditorias() {
        return auditorias;
    }

    public String getSalvoPor() {
        return salvoPor;
    }

    public Instant getSalvoEm() {
        return salvoEm;
    }
}
