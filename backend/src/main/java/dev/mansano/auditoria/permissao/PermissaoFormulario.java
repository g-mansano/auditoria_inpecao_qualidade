package dev.mansano.auditoria.permissao;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Nível de acesso de um usuário a um formulário (o admin acessa tudo sem precisar de linha aqui). */
@Entity
@Table(name = "permissao_formulario")
public class PermissaoFormulario {

    @Embeddable
    public record Chave(@Column(name = "usuario_id") Long usuarioId, @Column(name = "template_id") Long templateId)
            implements Serializable {
    }

    @EmbeddedId
    private Chave id;

    @Enumerated(EnumType.STRING)
    private NivelAcesso nivel;

    protected PermissaoFormulario() {
    }

    public PermissaoFormulario(Long usuarioId, Long templateId, NivelAcesso nivel) {
        this.id = new Chave(usuarioId, templateId);
        this.nivel = nivel;
    }

    public Chave getId() {
        return id;
    }

    public NivelAcesso getNivel() {
        return nivel;
    }

    public void setNivel(NivelAcesso nivel) {
        this.nivel = nivel;
    }
}
