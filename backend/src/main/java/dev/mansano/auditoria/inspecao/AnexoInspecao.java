package dev.mansano.auditoria.inspecao;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Foto/arquivo de um tópico da inspeção. Exclusão lógica. Id (UUID) gerado no aparelho. */
@Entity
@Table(name = "anexo_inspecao")
public class AnexoInspecao {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private InspecaoItem item;

    @Column(name = "nome_original")
    private String nomeOriginal;

    @Column(name = "content_type")
    private String contentType;

    private long tamanho;
    private String sha256;
    private String caminho;

    @Column(name = "enviado_por")
    private String enviadoPor;

    @Column(name = "enviado_em")
    private Instant enviadoEm = Instant.now();

    private boolean excluido;

    @Column(name = "excluido_por")
    private String excluidoPor;

    @Column(name = "excluido_em")
    private Instant excluidoEm;

    protected AnexoInspecao() {
    }

    public AnexoInspecao(String id, InspecaoItem item, String nomeOriginal, String contentType, long tamanho,
            String sha256, String caminho, String enviadoPor) {
        this.id = id;
        this.item = item;
        this.nomeOriginal = nomeOriginal;
        this.contentType = contentType;
        this.tamanho = tamanho;
        this.sha256 = sha256;
        this.caminho = caminho;
        this.enviadoPor = enviadoPor;
    }

    public void excluir(String usuario) {
        this.excluido = true;
        this.excluidoPor = usuario;
        this.excluidoEm = Instant.now();
    }

    public String getId() {
        return id;
    }

    public InspecaoItem getItem() {
        return item;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public String getContentType() {
        return contentType;
    }

    public long getTamanho() {
        return tamanho;
    }

    public String getSha256() {
        return sha256;
    }

    public String getCaminho() {
        return caminho;
    }

    public String getEnviadoPor() {
        return enviadoPor;
    }

    public Instant getEnviadoEm() {
        return enviadoEm;
    }

    public boolean isExcluido() {
        return excluido;
    }
}
