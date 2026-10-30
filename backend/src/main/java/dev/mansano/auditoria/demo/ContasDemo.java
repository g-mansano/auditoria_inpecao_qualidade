package dev.mansano.auditoria.demo;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dev.mansano.auditoria.usuario.Papel;

/** Contas de exemplo da demonstração pública. Todas usam a senha de {@code app.demo.senha}. */
public final class ContasDemo {

    private ContasDemo() {
    }

    public record Conta(String username, String nome, Papel papel, String descricao) {
    }

    public static final Conta ADMIN = new Conta("admin", "Administrador", Papel.ADMIN,
            "Usuários, permissões por formulário, reabrir, excluir e revisar formulários.");
    public static final Conta AUDITOR = new Conta("auditor", "Ana Auditora", Papel.AUDITOR,
            "Preenche os 5 checklists de auditoria e pode revisar formulários.");
    public static final Conta INSPETOR = new Conta("inspetor", "Caio Inspetor", Papel.AUDITOR,
            "Só acessa a Inspeção de OP's: abre a inspeção do dia, fotos, PDF.");
    public static final Conta LEITOR = new Conta("leitor", "Lia Leitora", Papel.LEITOR,
            "Somente leitura em todos os formulários.");

    public static final List<Conta> TODAS = List.of(ADMIN, AUDITOR, INSPETOR, LEITOR);

    private static final Set<String> LOGINS = TODAS.stream().map(Conta::username).collect(Collectors.toSet());

    public static boolean ehContaDemo(String username) {
        return username != null && LOGINS.contains(username.toLowerCase());
    }
}
