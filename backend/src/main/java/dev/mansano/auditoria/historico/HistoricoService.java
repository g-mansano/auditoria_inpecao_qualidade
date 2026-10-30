package dev.mansano.auditoria.historico;

import java.util.Objects;

import org.springframework.stereotype.Service;

/** Grava a trilha de auditoria. Deve ser chamado dentro da transação da alteração. */
@Service
public class HistoricoService {

    public static final String CRIACAO = "CRIACAO";
    public static final String CABECALHO = "CABECALHO";
    public static final String ITEM = "ITEM";
    public static final String CHECKOUT = "CHECKOUT";
    public static final String CHECKIN = "CHECKIN";
    public static final String CHECKOUT_FORCADO = "CHECKOUT_FORCADO";
    public static final String ANEXO_INCLUIDO = "ANEXO_INCLUIDO";
    public static final String ANEXO_EXCLUIDO = "ANEXO_EXCLUIDO";
    public static final String CONCLUSAO = "CONCLUSAO";
    public static final String REABERTURA = "REABERTURA";
    public static final String EXCLUSAO = "EXCLUSAO";
    /** Revisão do formulário levada para uma auditoria em andamento. */
    public static final String REVISAO_APLICADA = "REVISAO_APLICADA";

    private final RegistroAlteracaoRepository repo;

    public HistoricoService(RegistroAlteracaoRepository repo) {
        this.repo = repo;
    }

    public void evento(Long auditoriaId, Integer itemNumero, String evento, String detalhe, String usuario,
            String dispositivo) {
        repo.save(new RegistroAlteracao(auditoriaId, itemNumero, evento, null, null, detalhe, usuario, dispositivo));
    }

    /** Registra a mudança de um campo; não grava nada se o valor não mudou. */
    public void campo(Long auditoriaId, Integer itemNumero, String evento, String campo, Object antes, Object depois,
            String usuario, String dispositivo) {
        String a = antes == null ? null : antes.toString();
        String d = depois == null ? null : depois.toString();
        if (Objects.equals(a, d)) {
            return;
        }
        repo.save(new RegistroAlteracao(auditoriaId, itemNumero, evento, campo, a, d, usuario, dispositivo));
    }
}
