package dev.mansano.auditoria;

import dev.mansano.auditoria.permissao.NivelAcesso;
import dev.mansano.auditoria.permissao.PermissaoFormulario;
import dev.mansano.auditoria.permissao.PermissaoRepository;
import dev.mansano.auditoria.template.TemplateChecklist;
import dev.mansano.auditoria.template.TemplateRepository;
import dev.mansano.auditoria.usuario.Usuario;

/** Auxiliar dos testes: libera formulários para usuários criados direto no repositório. */
final class Permissoes {

    private Permissoes() {
    }

    /** Mesmo nível em todos os checklists de auditoria (o que a V13 fez com os usuários que já existiam). */
    static void auditorias(PermissaoRepository repo, TemplateRepository templates, Usuario u, NivelAcesso nivel) {
        templates.findAll().stream().filter(t -> !t.isInspecaoOp())
                .forEach(t -> repo.save(new PermissaoFormulario(u.getId(), t.getId(), nivel)));
    }

    static void formulario(PermissaoRepository repo, Usuario u, TemplateChecklist t, NivelAcesso nivel) {
        repo.save(new PermissaoFormulario(u.getId(), t.getId(), nivel));
    }
}
