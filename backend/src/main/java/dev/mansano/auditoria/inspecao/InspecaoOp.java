package dev.mansano.auditoria.inspecao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** Uma OP inspecionada (uma coluna da folha). Id gerado no aparelho. */
@Entity
@Table(name = "inspecao_op")
public class InspecaoOp {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dia_id")
    private InspecaoDia dia;

    private int ordem;

    @Column(name = "numero_op")
    private String numeroOp;

    @Column(name = "codigo_produto")
    private String codigoProduto;

    private boolean excluida;

    @Column(name = "criado_em")
    private Instant criadoEm = Instant.now();

    /** Momento da edição no aparelho; um envio atrasado da fila não sobrescreve um mais novo. */
    @Column(name = "alterado_em_cliente")
    private Instant alteradoEmCliente;

    @OneToMany(mappedBy = "op", cascade = CascadeType.ALL)
    @OrderBy("numero")
    private List<InspecaoItem> itens = new ArrayList<>();

    @OneToMany(mappedBy = "op", cascade = CascadeType.ALL)
    private List<InspecaoLote> lotes = new ArrayList<>();

    protected InspecaoOp() {
    }

    public InspecaoOp(String id, InspecaoDia dia, int ordem) {
        this.id = id;
        this.dia = dia;
        this.ordem = ordem;
    }

    public String getId() {
        return id;
    }

    public InspecaoDia getDia() {
        return dia;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public String getNumeroOp() {
        return numeroOp;
    }

    public void setNumeroOp(String numeroOp) {
        this.numeroOp = numeroOp;
    }

    public String getCodigoProduto() {
        return codigoProduto;
    }

    public void setCodigoProduto(String codigoProduto) {
        this.codigoProduto = codigoProduto;
    }

    public boolean isExcluida() {
        return excluida;
    }

    public void excluir() {
        this.excluida = true;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAlteradoEmCliente() {
        return alteradoEmCliente;
    }

    public void setAlteradoEmCliente(Instant alteradoEmCliente) {
        this.alteradoEmCliente = alteradoEmCliente;
    }

    public List<InspecaoItem> getItens() {
        return itens;
    }

    public List<InspecaoLote> getLotes() {
        return lotes;
    }

    public List<InspecaoLote> getLotesAtivos() {
        return lotes.stream().filter(l -> !l.isExcluido()).toList();
    }
}
