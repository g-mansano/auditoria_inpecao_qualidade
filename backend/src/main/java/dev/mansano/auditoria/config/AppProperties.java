package dev.mansano.auditoria.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurações da aplicação (prefixo {@code app} no application.properties).
 *
 * @param armazenamentoDir    pasta onde os anexos são gravados em disco
 * @param tokenValidadeDias   validade do token de acesso; longa para permitir trabalho offline
 * @param extensoesPermitidas extensões aceitas em anexos
 * @param adminInicial        usuário administrador criado no primeiro start, se não houver nenhum usuário
 * @param demo                modo demonstração pública (contas de exemplo, dados fictícios, reset diário)
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String armazenamentoDir,
        int tokenValidadeDias,
        List<String> extensoesPermitidas,
        AdminInicial adminInicial,
        Demo demo) {

    public AppProperties {
        if (demo == null) {
            demo = new Demo(false, null, null);
        }
    }

    public record AdminInicial(String username, String nome, String senha) {
    }

    /**
     * @param ativo     liga o modo demonstração
     * @param senha     senha pública de todas as contas de exemplo
     * @param resetCron quando o banco volta ao estado inicial (fuso America/Sao_Paulo)
     */
    public record Demo(boolean ativo, String senha, String resetCron) {
    }
}
