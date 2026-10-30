package dev.mansano.auditoria.usuario;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "token_acesso")
public class TokenAcesso {

    @Id
    @Column(name = "token_hash")
    private String tokenHash;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    protected TokenAcesso() {
    }

    public TokenAcesso(String tokenHash, Usuario usuario, Instant criadoEm, Instant expiraEm) {
        this.tokenHash = tokenHash;
        this.usuario = usuario;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }
}
