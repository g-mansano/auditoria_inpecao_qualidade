package dev.mansano.auditoria.usuario;

public enum Papel {
    /** Gerencia usuários, reabre auditorias e libera check-outs travados. */
    ADMIN,
    /** Cria e preenche auditorias. */
    AUDITOR,
    /** Apenas consulta e exporta. */
    LEITOR
}
