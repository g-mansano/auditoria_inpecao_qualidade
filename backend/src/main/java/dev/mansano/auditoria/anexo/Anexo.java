package dev.mansano.auditoria.anexo;

import java.time.Instant;
import java.util.UUID;

import dev.mansano.auditoria.auditoria.ItemAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Arquivo de evidência ligado a um item. O id (UUID) é gerado no aparelho, o que torna o
 * reenvio após queda de rede idempotente.
 */
@Entity
@Table(name = "anexo")
public class Anexo {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private ItemAuditoria item;

    @Column(name = "nome_original", nullable = false)
    private String nomeOriginal;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    private long tamanho;

    @Column(nullable = false)
    private String sha256;

    /** Caminho relativo à pasta de armazenamento. */
    @Column(nullable = false)
    private String caminho;

    @Column(name = "enviado_por", nullable = false)
    private String enviadoPor;

    @Column(name = "enviado_em", nullable = false)
    private Instant enviadoEm = Instant.now();

    private boolean excluido;

    @Column(name = "excluido_por")
    private String excluidoPor;

    @Column(name = "excluido_em")
    private Instant excluidoEm;

    protected Anexo() {
    }

    public Anexo(UUID id, ItemAuditoria item, String nomeOriginal, String contentType, long tamanho, String sha256,
            String caminho, String enviadoPor) {
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

    public UUID getId() {
        return id;
    }

    public ItemAuditoria getItem() {
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
