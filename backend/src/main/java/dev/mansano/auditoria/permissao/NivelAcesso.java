package dev.mansano.auditoria.permissao;

/** Acesso de um usuário a um formulário. Sem linha = nenhum acesso. */
public enum NivelAcesso {
    /** Consulta, imprime e baixa o PDF. */
    VER,
    /** Cria e edita enquanto em andamento; também vê. */
    PREENCHER;

    public boolean permite(NivelAcesso exigido) {
        return this == PREENCHER || exigido == VER;
    }
}
