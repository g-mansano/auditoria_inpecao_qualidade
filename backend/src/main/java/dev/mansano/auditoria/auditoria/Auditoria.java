package dev.mansano.auditoria.auditoria;

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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String numero;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    /** Nomes dos auditores, um por linha. */
    @Column(nullable = false)
    private String auditores;

    private String escopo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAuditoria status = StatusAuditoria.EM_ANDAMENTO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id")
    private TemplateChecklist template;

    /** Quem está com o check-out (edição exclusiva). Nulo = livre. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "editor_id")
    private Usuario editor;

    /** Aparelho do check-out; o mesmo usuário não edita em dois aparelhos ao mesmo tempo. */
    @Column(name = "editor_dispositivo")
    private String editorDispositivo;

    @Column(name = "checkout_em")
    private Instant checkoutEm;

    @Column(name = "criado_por", nullable = false)
    private String criadoPor;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm = Instant.now();

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    private boolean excluida;

    @Column(name = "excluida_por")
    private String excluidaPor;

    @Column(name = "excluida_em")
    private Instant excluidaEm;

    @Version
    private long versao;

    /** Concluída ao menos uma vez: fica na revisão em que foi feita, mesmo se reaberta. */
    @Column(name = "revisao_congelada")
    private boolean revisaoCongelada;

    /** Cópia do que a auditoria congelada imprime (identificação, título e rodapé da revisão de então). */
    @Column(name = "snap_identificacao")
    private String snapIdentificacao;

    @Column(name = "snap_titulo")
    private String snapTitulo;

    @Column(name = "snap_rodape")
    private String snapRodape;

    @OneToMany(mappedBy = "auditoria", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numero")
    private List<ItemAuditoria> itens = new ArrayList<>();

    protected Auditoria() {
    }

    public Auditoria(String numero, LocalDate dataInicio, LocalDate dataFim, String auditores, String escopo,
            TemplateChecklist template, String criadoPor) {
        this.numero = numero;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.auditores = auditores;
        this.escopo = escopo;
        this.template = template;
        this.criadoPor = criadoPor;
    }

    public boolean emCheckoutPor(Long usuarioId, String dispositivo) {
        return editor != null && editor.getId().equals(usuarioId) && editorDispositivo != null
                && editorDispositivo.equals(dispositivo);
    }

    public void checkout(Usuario usuario, String dispositivo) {
        this.editor = usuario;
        this.editorDispositivo = dispositivo;
        this.checkoutEm = Instant.now();
    }

    public void liberar() {
        this.editor = null;
        this.editorDispositivo = null;
        this.checkoutEm = null;
    }

    /** Exclusão lógica: some das listas, mas o registro e o histórico permanecem no banco. */
    public void excluir(String usuario) {
        this.excluida = true;
        this.excluidaPor = usuario;
        this.excluidaEm = Instant.now();
        liberar();
    }

    /** Na primeira conclusão, guarda a revisão do formulário; revisões futuras não a alteram. */
    public void congelar() {
        if (revisaoCongelada) {
            return;
        }
        this.revisaoCongelada = true;
        this.snapIdentificacao = template.getIdentificacao();
        this.snapTitulo = template.getTitulo();
        this.snapRodape = template.getRodape();
    }

    public boolean isRevisaoCongelada() {
        return revisaoCongelada;
    }

    public String getSnapIdentificacao() {
        return snapIdentificacao;
    }

    public String getSnapTitulo() {
        return snapTitulo;
    }

    public String getSnapRodape() {
        return snapRodape;
    }

    /** Itens da auditoria, sem os removidos por revisão. */
    public List<ItemAuditoria> getItensAtivos() {
        return itens.stream().filter(i -> !i.isRemovido()).toList();
    }

    public boolean isExcluida() {
        return excluida;
    }

    public void tocar() {
        this.atualizadoEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(LocalDate dataInicio) {
        this.dataInicio = dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public void setDataFim(LocalDate dataFim) {
        this.dataFim = dataFim;
    }

    public String getAuditores() {
        return auditores;
    }

    public void setAuditores(String auditores) {
        this.auditores = auditores;
    }

    public String getEscopo() {
        return escopo;
    }

    public void setEscopo(String escopo) {
        this.escopo = escopo;
    }

    public StatusAuditoria getStatus() {
        return status;
    }

    public void setStatus(StatusAuditoria status) {
        this.status = status;
    }

    public TemplateChecklist getTemplate() {
        return template;
    }

    public Usuario getEditor() {
        return editor;
    }

    public String getEditorDispositivo() {
        return editorDispositivo;
    }

    public Instant getCheckoutEm() {
        return checkoutEm;
    }

    public String getCriadoPor() {
        return criadoPor;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public Instant getConcluidoEm() {
        return concluidoEm;
    }

    public void setConcluidoEm(Instant concluidoEm) {
        this.concluidoEm = concluidoEm;
    }

    public long getVersao() {
        return versao;
    }

    public List<ItemAuditoria> getItens() {
        return itens;
    }
}
