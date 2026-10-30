package dev.mansano.auditoria.inspecao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Lote do insumo anotado na OP (antes ia no verso da folha). Id gerado no aparelho. */
@Entity
@Table(name = "inspecao_lote")
public class InspecaoLote {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "op_id")
    private InspecaoOp op;

    private String lote;

    @Column(name = "data_fabricacao")
    private String dataFabricacao;

    private boolean excluido;

    protected InspecaoLote() {
    }

    public InspecaoLote(String id, InspecaoOp op) {
        this.id = id;
        this.op = op;
    }

    public String getId() {
        return id;
    }

    public String getLote() {
        return lote;
    }

    public void setLote(String lote) {
        this.lote = lote;
    }

    public String getDataFabricacao() {
        return dataFabricacao;
    }

    public void setDataFabricacao(String dataFabricacao) {
        this.dataFabricacao = dataFabricacao;
    }

    public boolean isExcluido() {
        return excluido;
    }

    public void setExcluido(boolean excluido) {
        this.excluido = excluido;
    }
}
