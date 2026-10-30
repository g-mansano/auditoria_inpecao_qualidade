package dev.mansano.auditoria.inspecao;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Um tópico da OP (cópia da pergunta do formulário quando a OP foi criada). */
@Entity
@Table(name = "inspecao_item")
public class InspecaoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "op_id")
    private InspecaoOp op;

    private int numero;
    private String topico;
    private String escala;
    private String valor;
    private String observacao;

    protected InspecaoItem() {
    }

    public InspecaoItem(InspecaoOp op, int numero, String topico, String escala) {
        this.op = op;
        this.numero = numero;
        this.topico = topico;
        this.escala = escala;
    }

    public Long getId() {
        return id;
    }

    public InspecaoOp getOp() {
        return op;
    }

    public int getNumero() {
        return numero;
    }

    public String getTopico() {
        return topico;
    }

    public String getEscala() {
        return escala;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }
}
