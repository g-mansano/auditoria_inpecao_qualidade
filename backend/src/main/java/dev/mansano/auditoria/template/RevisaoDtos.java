package dev.mansano.auditoria.template;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Objetos da tela de revisão de formulário. */
public final class RevisaoDtos {

    private RevisaoDtos() {
    }

    /** {@code respondidas}: quantas auditorias em andamento já responderam esta pergunta. */
    public record PerguntaRevDto(Long id, int numero, String secao, String grupo, String clausula, String texto,
            String processoPadrao, String documentosPadrao, String orientacaoPadrao, String responsavelPadrao,
            int respondidas) {
    }

    public record RevisaoDto(Long templateId, String nome, String codigo, String revisao, String titulo, String rodape,
            long versao, List<PerguntaRevDto> perguntas) {
    }

    /** {@code id} nulo = pergunta nova. A posição na lista vira o número (1..N). */
    public record PerguntaReq(
            Long id,
            @NotBlank @Size(max = 20) String secao,
            @Size(max = 200) String grupo,
            @Size(max = 60) String clausula,
            @NotBlank @Size(max = 2000) String texto,
            @Size(max = 500) String processoPadrao,
            @Size(max = 2000) String documentosPadrao,
            @Size(max = 2000) String orientacaoPadrao,
            @Size(max = 150) String responsavelPadrao) {
    }

    /** {@code nova}: abre a revisão seguinte; senão, reescreve a atual. */
    public record SalvarRevisaoReq(
            boolean nova,
            long versao,
            @NotBlank @Size(max = 200) String titulo,
            @NotEmpty List<@Valid PerguntaReq> perguntas) {
    }

    public record DetalheRevisaoDto(Integer numero, String tipo, String antes, String depois) {
    }

    public record HistoricoRevisaoDto(Long id, String revisao, boolean nova, int alteradas, int novas, int removidas,
            int auditorias, String salvoPor, java.time.Instant salvoEm, List<DetalheRevisaoDto> detalhes) {
    }

    public record ResultadoRevisao(String revisao, String rodape, int alteradas, int novas, int removidas,
            int auditorias, int itensReabertos) {
    }
}
