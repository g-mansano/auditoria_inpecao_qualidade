package dev.mansano.auditoria.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pergunta")
public class Pergunta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private TemplateChecklist template;

    private int numero;
    private String clausula;
    private String texto;

    @Column(name = "processo_padrao")
    private String processoPadrao;

    @Column(name = "documentos_padrao")
    private String documentosPadrao;

    /** CHECKLIST ou CAMPO (roteiro de inspeção por setor). */
    private String secao;

    /** Capítulo (ISO), etapa (ANVISA) ou setor (roteiro de campo). */
    private String grupo;

    /** Como verificar (ex.: "Visual", "Documentação SGQ"). */
    @Column(name = "orientacao_padrao")
    private String orientacaoPadrao;

    @Column(name = "responsavel_padrao")
    private String responsavelPadrao;

    /** Escala própria (ex.: EPI = S/N/P). Nulo = escala do formulário. */
    private String escala;

    protected Pergunta() {
    }

    /** Pergunta nova incluída numa revisão. */
    public Pergunta(TemplateChecklist template) {
        this.template = template;
    }

    /** Copia o que veio da tela de revisão; o número é a posição na lista. */
    public void definir(int numero, String secao, String grupo, String clausula, String texto, String processoPadrao,
            String documentosPadrao, String orientacaoPadrao, String responsavelPadrao) {
        this.numero = numero;
        this.secao = secao;
        this.grupo = grupo;
        this.clausula = clausula;
        this.texto = texto;
        this.processoPadrao = processoPadrao;
        this.documentosPadrao = documentosPadrao;
        this.orientacaoPadrao = orientacaoPadrao;
        this.responsavelPadrao = responsavelPadrao;
    }

    public TemplateChecklist getTemplate() {
        return template;
    }

    public Long getId() {
        return id;
    }

    public int getNumero() {
        return numero;
    }

    public String getClausula() {
        return clausula;
    }

    public String getTexto() {
        return texto;
    }

    public String getProcessoPadrao() {
        return processoPadrao;
    }

    public String getDocumentosPadrao() {
        return documentosPadrao;
    }

    public String getSecao() {
        return secao;
    }

    public String getGrupo() {
        return grupo;
    }

    public String getOrientacaoPadrao() {
        return orientacaoPadrao;
    }

    public String getEscala() {
        return escala;
    }

    public String getResponsavelPadrao() {
        return responsavelPadrao;
    }
}
