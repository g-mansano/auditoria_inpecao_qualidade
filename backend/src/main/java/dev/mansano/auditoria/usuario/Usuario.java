package dev.mansano.auditoria.usuario;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String nome;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Papel papel;

    private boolean ativo = true;

    /** Pode abrir e editar revisões dos formulários (ADMIN sempre pode). */
    @Column(name = "pode_revisar")
    private boolean podeRevisar;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    protected Usuario() {
    }

    public Usuario(String username, String nome, String senhaHash, Papel papel) {
        this.username = username;
        this.nome = nome;
        this.senhaHash = senhaHash;
        this.papel = papel;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public Papel getPapel() {
        return papel;
    }

    public void setPapel(Papel papel) {
        this.papel = papel;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public boolean isPodeRevisar() {
        return podeRevisar || papel == Papel.ADMIN;
    }

    /** Valor gravado, sem contar o papel de admin. */
    public boolean isPodeRevisarMarcado() {
        return podeRevisar;
    }

    public void setPodeRevisar(boolean podeRevisar) {
        this.podeRevisar = podeRevisar;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
