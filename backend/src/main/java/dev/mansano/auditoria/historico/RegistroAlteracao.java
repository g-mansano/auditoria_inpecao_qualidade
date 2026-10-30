package dev.mansano.auditoria.historico;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Linha da trilha de auditoria. Só recebe inserts; nunca é alterada nem apagada. */
@Entity
@Table(name = "registro_alteracao")
public class RegistroAlteracao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auditoria_id", nullable = false, updatable = false)
    private Long auditoriaId;

    @Column(name = "item_numero", updatable = false)
    private Integer itemNumero;

    @Column(nullable = false, updatable = false)
    private String evento;

    @Column(updatable = false)
    private String campo;

    @Column(name = "valor_anterior", updatable = false)
    private String valorAnterior;

    @Column(name = "valor_novo", updatable = false)
    private String valorNovo;

    @Column(nullable = false, updatable = false)
    private String usuario;

    @Column(updatable = false)
    private String dispositivo;

    @Column(name = "registrado_em", nullable = false, updatable = false)
    private Instant registradoEm = Instant.now();

    protected RegistroAlteracao() {
    }

    public RegistroAlteracao(Long auditoriaId, Integer itemNumero, String evento, String campo, String valorAnterior,
            String valorNovo, String usuario, String dispositivo) {
        this.auditoriaId = auditoriaId;
        this.itemNumero = itemNumero;
        this.evento = evento;
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
        this.usuario = usuario;
        this.dispositivo = dispositivo;
    }

    public Long getId() {
        return id;
    }

    public Long getAuditoriaId() {
        return auditoriaId;
    }

    public Integer getItemNumero() {
        return itemNumero;
    }

    public String getEvento() {
        return evento;
    }

    public String getCampo() {
        return campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNovo() {
        return valorNovo;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getDispositivo() {
        return dispositivo;
    }

    public Instant getRegistradoEm() {
        return registradoEm;
    }
}
