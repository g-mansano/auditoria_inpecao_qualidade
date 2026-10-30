package dev.mansano.auditoria.seguranca;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.mansano.auditoria.comum.NegocioException;

/**
 * Proteção contra força bruta no endpoint de login.
 * Bloqueia temporariamente após 5 tentativas inválidas seguidas em 5 minutos.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_TENTATIVAS = 5;
    private static final Duration JANELA_BLOQUEIO = Duration.ofMinutes(5);

    private record Tentativas(int contador, Instant primeiroErro) {
        Tentativas incrementar() {
            return new Tentativas(contador + 1, primeiroErro);
        }
    }

    private final ConcurrentHashMap<String, Tentativas> falhas = new ConcurrentHashMap<>();

    public void verificarBloqueio(String chave) {
        Tentativas t = falhas.get(chave.toLowerCase());
        if (t != null && t.contador >= MAX_TENTATIVAS) {
            if (Duration.between(t.primeiroErro, Instant.now()).compareTo(JANELA_BLOQUEIO) < 0) {
                throw new NegocioException(HttpStatus.TOO_MANY_REQUESTS, "MUITAS_TENTATIVAS",
                        "Muitas tentativas de login inválidas. Tente novamente em 5 minutos.");
            } else {
                falhas.remove(chave.toLowerCase());
            }
        }
    }

    public void registrarFalha(String chave) {
        String k = chave.toLowerCase();
        falhas.compute(k, (key, atual) -> {
            Instant agora = Instant.now();
            if (atual == null || Duration.between(atual.primeiroErro, agora).compareTo(JANELA_BLOQUEIO) >= 0) {
                return new Tentativas(1, agora);
            }
            return atual.incrementar();
        });
    }

    public void registrarSucesso(String chave) {
        falhas.remove(chave.toLowerCase());
    }

    @Scheduled(fixedRate = 300000)
    public void limparExpirados() {
        Instant agora = Instant.now();
        falhas.entrySet().removeIf(e -> Duration.between(e.getValue().primeiroErro, agora).compareTo(JANELA_BLOQUEIO) >= 0);
    }
}
