package dev.mansano.auditoria.template;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.mansano.auditoria.auditoria.Conformidade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** Uma revisão do formulário de checklist (ex.: FQ-101 Rev.06). */
@Entity
@Table(name = "template_checklist")
public class TemplateChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;
    private String revisao;
    private String titulo;

    private boolean ativo;

    /** Nome curto para a tela inicial (ex.: "ISO 13485", "ANVISA"). */
    private String nome;

    private int ordem;

    @Column(name = "rotulo_referencia")
    private String rotuloReferencia;

    @Column(name = "usa_processo")
    private boolean usaProcesso;

    @Column(name = "escopo_padrao")
    private String escopoPadrao;

    /** Textos rápidos para o campo de evidências, separados por "|". */
    @Column(name = "sugestoes_evidencia")
    private String sugestoesEvidencia;

    /** "Auditoria Interna" ou "Auditoria Housekeeping" (cabeçalho "... Nº"). */
    @Column(name = "rotulo_auditoria")
    private String rotuloAuditoria;

    @Column(name = "usa_documentos")
    private boolean usaDocumentos;

    /** Campo "Local / área" por item (Housekeeping). */
    @Column(name = "usa_local")
    private boolean usaLocal;

    /** Escala de resposta: "valor:sigla:rótulo|..." (ex.: "C:S:Sim|NC:N:Não|NA:NA:Não se aplica"). */
    @Column(name = "opcoes_conformidade")
    private String opcoesConformidade;

    /** Conclusão exige nº de registro em toda resposta NC. */
    @Column(name = "exige_registro_nc")
    private boolean exigeRegistroNc;

    /** Rodapé de impressão, igual ao do formulário original (ex.: "FQ-101 - Rev. 06 - Data: 02/03/2026"). */
    private String rodape;

    /** AUDITORIA (checklists de auditoria) ou INSPECAO_OP (FQ-106). */
    private String tipo;

    /** Sobe a cada salvamento de revisão; dois revisores com a mesma versão: o segundo recebe 409. */
    private long versao;

    @OneToMany(mappedBy = "template")
    @OrderBy("numero")
    private List<Pergunta> perguntas = new ArrayList<>();

    protected TemplateChecklist() {
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getRevisao() {
        return revisao;
    }

    public void incrementarVersao() {
        versao++;
    }

    public long getVersao() {
        return versao;
    }

    public void setRevisao(String revisao) {
        this.revisao = revisao;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public void setRodape(String rodape) {
        this.rodape = rodape;
    }

    public String getTitulo() {
        return titulo;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public List<Pergunta> getPerguntas() {
        return perguntas;
    }

    public String getNome() {
        return nome != null ? nome : codigo;
    }

    public int getOrdem() {
        return ordem;
    }

    public String getRotuloReferencia() {
        return rotuloReferencia;
    }

    public boolean isUsaProcesso() {
        return usaProcesso;
    }

    public String getEscopoPadrao() {
        return escopoPadrao;
    }

    public List<String> getSugestoesEvidencia() {
        if (sugestoesEvidencia == null || sugestoesEvidencia.isBlank()) {
            return List.of();
        }
        return List.of(sugestoesEvidencia.split("\\|"));
    }

    public String getRotuloAuditoria() {
        return rotuloAuditoria != null ? rotuloAuditoria : "Auditoria Interna";
    }

    public boolean isUsaDocumentos() {
        return usaDocumentos;
    }

    public boolean isUsaLocal() {
        return usaLocal;
    }

    public String getTipo() {
        return tipo != null ? tipo : "AUDITORIA";
    }

    public boolean isInspecaoOp() {
        return "INSPECAO_OP".equals(tipo);
    }

    public String getRodape() {
        return rodape;
    }

    public boolean isExigeRegistroNc() {
        return exigeRegistroNc;
    }

    /** Uma opção da escala de resposta: valor gravado (C/NC/OM/NA), sigla e rótulo exibidos. */
    public record Opcao(Conformidade valor, String sigla, String rotulo) {
    }

    public List<Opcao> getOpcoes() {
        String fonte = opcoesConformidade != null ? opcoesConformidade
                : "C:C:Conforme|NC:NC:Não Conforme|OM:OM:Oportunidade de Melhoria|NA:NA:Não Aplicável";
        return Arrays.stream(fonte.split("\\|"))
                .map(o -> o.split(":", 3))
                .map(p -> new Opcao(Conformidade.valueOf(p[0]), p[1], p[2]))
                .toList();
    }

    /** Sigla exibida para um valor (ex.: C -> "S" no INMETRO). */
    public String sigla(Conformidade c) {
        return getOpcoes().stream().filter(o -> o.valor() == c).map(Opcao::sigla).findFirst().orElse(c.name());
    }

    public boolean aceita(Conformidade c) {
        return getOpcoes().stream().anyMatch(o -> o.valor() == c);
    }

    /** Ex.: "FQ-101 Rev.06". */
    public String getIdentificacao() {
        return codigo + " Rev." + revisao;
    }
}
