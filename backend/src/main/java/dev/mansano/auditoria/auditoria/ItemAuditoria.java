package dev.mansano.auditoria.auditoria;

import java.time.Instant;

import dev.mansano.auditoria.template.Pergunta;

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
import jakarta.persistence.Table;

/** Uma linha do checklist dentro de uma auditoria. */
@Entity
@Table(name = "item_auditoria")
public class ItemAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auditoria_id")
    private Auditoria auditoria;

    private int numero;
    private String clausula;
    private String pergunta;
    private String processo;
    private String documentos;
    private String evidencias;

    @Enumerated(EnumType.STRING)
    private Conformidade conformidade;

    private String rnc;

    /** CHECKLIST ou CAMPO (roteiro de inspeção por setor). */
    private String secao;

    /** Capítulo (ISO), etapa (ANVISA) ou setor (roteiro de campo). */
    private String grupo;

    /** Como verificar; vem do formulário e pode ser ajustado na auditoria. */
    private String orientacao;

    /** Auditor responsável pelo item (roteiro de campo). */
    private String responsavel;

    /** Local / área onde foi verificado (Housekeeping). */
    private String local;

    @Column(name = "atualizado_em")
    private Instant atualizadoEm;

    @Column(name = "atualizado_por")
    private String atualizadoPor;

    /** Momento da edição no aparelho; evita que um envio atrasado da fila sobrescreva um mais novo. */
    @Column(name = "alterado_em_cliente")
    private Instant alteradoEmCliente;

    /** Pergunta do formulário que originou o item; liga o item às revisões. */
    @Column(name = "pergunta_id")
    private Long perguntaId;

    /** Revisão em que a pergunta mudou depois de respondida; o inspetor precisa responder de novo. */
    @Column(name = "alterada_na_revisao")
    private String alteradaNaRevisao;

    @Column(name = "alterada_em")
    private Instant alteradaEm;

    /** Pergunta removida numa revisão: o item sai da auditoria e fica só no histórico. */
    private boolean removido;

    @Column(name = "removido_na_revisao")
    private String removidoNaRevisao;

    /** O inspetor já viu a janela de perguntas removidas. */
    @Column(name = "removido_visto")
    private boolean removidoVisto;

    protected ItemAuditoria() {
    }

    public ItemAuditoria(Auditoria auditoria, Pergunta p) {
        this.auditoria = auditoria;
        this.perguntaId = p.getId();
        this.numero = p.getNumero();
        this.secao = p.getSecao();
        this.grupo = p.getGrupo();
        this.clausula = p.getClausula();
        this.pergunta = p.getTexto();
        this.processo = p.getProcessoPadrao();
        this.documentos = p.getDocumentosPadrao();
        this.orientacao = p.getOrientacaoPadrao();
        this.responsavel = p.getResponsavelPadrao();
    }

    public Long getId() {
        return id;
    }

    public Auditoria getAuditoria() {
        return auditoria;
    }

    public int getNumero() {
        return numero;
    }

    public String getClausula() {
        return clausula;
    }

    public String getPergunta() {
        return pergunta;
    }

    public String getProcesso() {
        return processo;
    }

    public void setProcesso(String processo) {
        this.processo = processo;
    }

    public String getDocumentos() {
        return documentos;
    }

    public void setDocumentos(String documentos) {
        this.documentos = documentos;
    }

    public String getEvidencias() {
        return evidencias;
    }

    public void setEvidencias(String evidencias) {
        this.evidencias = evidencias;
    }

    public Conformidade getConformidade() {
        return conformidade;
    }

    public void setConformidade(Conformidade conformidade) {
        this.conformidade = conformidade;
    }

    public String getRnc() {
        return rnc;
    }

    public void setRnc(String rnc) {
        this.rnc = rnc;
    }

    public String getSecao() {
        return secao;
    }

    public String getGrupo() {
        return grupo;
    }

    public String getOrientacao() {
        return orientacao;
    }

    public void setOrientacao(String orientacao) {
        this.orientacao = orientacao;
    }

    public String getResponsavel() {
        return responsavel;
    }

    public void setResponsavel(String responsavel) {
        this.responsavel = responsavel;
    }

    public String getLocal() {
        return local;
    }

    public void setLocal(String local) {
        this.local = local;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public String getAtualizadoPor() {
        return atualizadoPor;
    }

    public Instant getAlteradoEmCliente() {
        return alteradoEmCliente;
    }

    public Long getPerguntaId() {
        return perguntaId;
    }

    public String getAlteradaNaRevisao() {
        return alteradaNaRevisao;
    }

    public Instant getAlteradaEm() {
        return alteradaEm;
    }

    public boolean isRemovido() {
        return removido;
    }

    public String getRemovidoNaRevisao() {
        return removidoNaRevisao;
    }

    public boolean isRemovidoVisto() {
        return removidoVisto;
    }

    public void marcarRemovidoVisto() {
        this.removidoVisto = true;
    }

    /** Texto/referência/seção/grupo/número vindos da revisão. */
    public void aplicarPergunta(Pergunta p) {
        this.numero = p.getNumero();
        this.secao = p.getSecao();
        this.grupo = p.getGrupo();
        this.clausula = p.getClausula();
        this.pergunta = p.getTexto();
    }

    /** A pergunta mudou depois de respondida: a conformidade volta a pendente, o resto fica. */
    public void exigirNovaResposta(String revisao) {
        this.conformidade = null;
        this.alteradaNaRevisao = revisao;
        this.alteradaEm = Instant.now();
    }

    public void limparAvisoDeAlteracao() {
        this.alteradaNaRevisao = null;
        this.alteradaEm = null;
    }

    public void remover(String revisao) {
        this.removido = true;
        this.removidoNaRevisao = revisao;
        this.removidoVisto = false;
    }

    /** Tem algo que o inspetor preencheu (sem contar os anexos, conferidos à parte). */
    public boolean temResposta() {
        return conformidade != null || evidencias != null || rnc != null || local != null;
    }

    public void marcarAtualizado(String usuario, Instant emCliente) {
        this.atualizadoEm = Instant.now();
        this.atualizadoPor = usuario;
        this.alteradoEmCliente = emCliente;
    }
}
