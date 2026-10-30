package dev.mansano.auditoria.inspecao;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Trilha da inspeção. Só recebe inserts. */
@Entity
@Table(name = "registro_inspecao")
public class RegistroInspecao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dia_id", updatable = false)
    private String diaId;

    @Column(name = "op_id", updatable = false)
    private String opId;

    @Column(name = "numero_op", updatable = false)
    private String numeroOp;

    @Column(updatable = false)
    private String evento;

    @Column(updatable = false)
    private String campo;

    @Column(name = "valor_anterior", updatable = false)
    private String valorAnterior;

    @Column(name = "valor_novo", updatable = false)
    private String valorNovo;

    @Column(updatable = false)
    private String usuario;

    @Column(updatable = false)
    private String dispositivo;

    @Column(name = "registrado_em", updatable = false)
    private Instant registradoEm = Instant.now();

    protected RegistroInspecao() {
    }

    public RegistroInspecao(String diaId, String opId, String numeroOp, String evento, String campo, String antes,
            String depois, String usuario, String dispositivo) {
        this.diaId = diaId;
        this.opId = opId;
        this.numeroOp = numeroOp;
        this.evento = evento;
        this.campo = campo;
        this.valorAnterior = antes;
        this.valorNovo = depois;
        this.usuario = usuario;
        this.dispositivo = dispositivo;
    }

    public Long getId() {
        return id;
    }

    public String getOpId() {
        return opId;
    }

    public String getNumeroOp() {
        return numeroOp;
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
