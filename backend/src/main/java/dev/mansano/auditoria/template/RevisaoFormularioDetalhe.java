package dev.mansano.auditoria.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** O que mudou numa pergunta num salvamento de revisão (ALTERADA, NOVA, REMOVIDA, TITULO). Só INSERT. */
@Entity
@Table(name = "revisao_formulario_detalhe")
public class RevisaoFormularioDetalhe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "revisao_id")
    private Long revisaoId;

    private Integer numero;
    private String tipo;
    private String antes;
    private String depois;

    protected RevisaoFormularioDetalhe() {
    }

    public RevisaoFormularioDetalhe(Long revisaoId, Integer numero, String tipo, String antes, String depois) {
        this.revisaoId = revisaoId;
        this.numero = numero;
        this.tipo = tipo;
        this.antes = antes;
        this.depois = depois;
    }

    public Long getRevisaoId() {
        return revisaoId;
    }

    public Integer getNumero() {
        return numero;
    }

    public String getTipo() {
        return tipo;
    }

    public String getAntes() {
        return antes;
    }

    public String getDepois() {
        return depois;
    }
}
