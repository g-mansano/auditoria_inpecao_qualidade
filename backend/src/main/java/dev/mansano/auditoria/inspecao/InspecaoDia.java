package dev.mansano.auditoria.inspecao;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import dev.mansano.auditoria.template.TemplateChecklist;
import dev.mansano.auditoria.usuario.Usuario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** Inspeção de OPs de um inspetor num dia (substitui a folha do FQ-106). Id gerado no aparelho. */
@Entity
@Table(name = "inspecao_dia")
public class InspecaoDia {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id")
    private TemplateChecklist template;

    private LocalDate data;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspetor_id")
    private Usuario inspetor;

    @Enumerated(EnumType.STRING)
    private StatusInspecao status = StatusInspecao.EM_ANDAMENTO;

    @Column(name = "finalizada_em")
    private Instant finalizadaEm;

    @Column(name = "criado_em")
    private Instant criadoEm = Instant.now();

    @Column(name = "atualizado_em")
    private Instant atualizadoEm = Instant.now();

    /** Exclusão lógica (admin): some das listas; registro e histórico permanecem. */
    private boolean excluida;

    @Column(name = "excluida_por")
    private String excluidaPor;

    @Column(name = "excluida_em")
    private Instant excluidaEm;

    @OneToMany(mappedBy = "dia", cascade = CascadeType.ALL)
    @OrderBy("ordem")
    private List<InspecaoOp> ops = new ArrayList<>();

    protected InspecaoDia() {
    }

    public InspecaoDia(String id, TemplateChecklist template, LocalDate data, Usuario inspetor) {
        this.id = id;
        this.template = template;
        this.data = data;
        this.inspetor = inspetor;
    }

    public void tocar() {
        this.atualizadoEm = Instant.now();
    }

    public void finalizar() {
        this.status = StatusInspecao.FINALIZADA;
        this.finalizadaEm = Instant.now();
        tocar();
    }

    public void reabrir() {
        this.status = StatusInspecao.EM_ANDAMENTO;
        this.finalizadaEm = null;
        tocar();
    }

    public void excluir(String usuario) {
        this.excluida = true;
        this.excluidaPor = usuario;
        this.excluidaEm = Instant.now();
    }

    public boolean isExcluida() {
        return excluida;
    }

    public List<InspecaoOp> getOpsAtivas() {
        return ops.stream().filter(o -> !o.isExcluida()).toList();
    }

    public String getId() {
        return id;
    }

    public TemplateChecklist getTemplate() {
        return template;
    }

    public LocalDate getData() {
        return data;
    }

    public Usuario getInspetor() {
        return inspetor;
    }

    public StatusInspecao getStatus() {
        return status;
    }

    public Instant getFinalizadaEm() {
        return finalizadaEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public List<InspecaoOp> getOps() {
        return ops;
    }
}
