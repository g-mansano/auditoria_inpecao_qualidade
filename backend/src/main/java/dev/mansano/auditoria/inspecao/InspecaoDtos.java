package dev.mansano.auditoria.inspecao;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Objetos da inspeção de OPs trocados com o front-end. */
public final class InspecaoDtos {

    private InspecaoDtos() {
    }

    public record InspetorDto(Long id, String nome, String username) {
    }

    /** Tópico do formulário vigente: o aparelho copia estes tópicos ao criar uma OP offline. */
    public record TopicoDto(int numero, String texto, String escala) {
    }

    public record AnexoDto(String id, String opId, int itemNumero, String nomeOriginal, String contentType,
            long tamanho, String sha256, String enviadoPor, Instant enviadoEm) {
        static AnexoDto de(AnexoInspecao a) {
            return new AnexoDto(a.getId(), a.getItem().getOp().getId(), a.getItem().getNumero(), a.getNomeOriginal(),
                    a.getContentType(), a.getTamanho(), a.getSha256(), a.getEnviadoPor(), a.getEnviadoEm());
        }
    }

    public record ItemDto(int numero, String topico, String escala, String valor, String observacao,
            List<AnexoDto> anexos) {
    }

    public record LoteDto(String id, String lote, String dataFabricacao) {
    }

    public record OpDto(String id, int ordem, String numeroOp, String codigoProduto, Instant alteradoEmCliente,
            List<ItemDto> itens, List<LoteDto> lotes) {
    }

    public record DiaResumo(String id, Long templateId, LocalDate data, InspetorDto inspetor, StatusInspecao status,
            int totalOps, Map<String, Long> contagem, long naoConformes, Instant atualizadoEm) {
    }

    public record DiaDetalhe(String id, Long templateId, String formulario, String rodape, LocalDate data,
            InspetorDto inspetor, StatusInspecao status, Instant finalizadaEm, Instant criadoEm, Instant atualizadoEm,
            List<TopicoDto> topicos, List<OpDto> ops) {
    }

    public record NovoDiaReq(@NotNull Long templateId, @NotNull LocalDate data) {
    }

    /** {@code topico}/{@code escala}: os que o aparelho mostrou; usados só quando a OP é criada. */
    public record ItemReq(int numero, @Size(max = 4) String valor, @Size(max = 4000) String observacao,
            @Size(max = 2000) String topico, @Size(max = 10) String escala) {
    }

    public record LoteReq(@NotNull @Size(max = 36) String id, @Size(max = 120) String lote,
            @Size(max = 40) String dataFabricacao) {
    }

    /** OP inteira (a fila do aparelho manda sempre o estado completo; o envio é idempotente). */
    public record OpReq(
            int ordem,
            @Size(max = 60) String numeroOp,
            @Size(max = 120) String codigoProduto,
            List<@Valid ItemReq> itens,
            List<@Valid LoteReq> lotes,
            Instant alteradoEmCliente) {
    }

    public record HistoricoDto(Long id, String opId, String numeroOp, String evento, String campo,
            String valorAnterior, String valorNovo, String usuario, String dispositivo, Instant registradoEm) {
        static HistoricoDto de(RegistroInspecao r) {
            return new HistoricoDto(r.getId(), r.getOpId(), r.getNumeroOp(), r.getEvento(), r.getCampo(),
                    r.getValorAnterior(), r.getValorNovo(), r.getUsuario(), r.getDispositivo(), r.getRegistradoEm());
        }
    }
}
