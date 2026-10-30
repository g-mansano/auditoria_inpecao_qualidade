package dev.mansano.auditoria.auditoria;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.mansano.auditoria.anexo.Anexo;
import dev.mansano.auditoria.historico.RegistroAlteracao;
import dev.mansano.auditoria.template.TemplateChecklist;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Objetos trocados com o front-end. */
public final class AuditoriaDtos {

    private AuditoriaDtos() {
    }

    public record EditorDto(Long usuarioId, String nome, String dispositivo, Instant desde) {
        static EditorDto de(Auditoria a) {
            if (a.getEditor() == null) {
                return null;
            }
            return new EditorDto(a.getEditor().getId(), a.getEditor().getNome(), a.getEditorDispositivo(),
                    a.getCheckoutEm());
        }
    }

    public record AuditoriaResumo(
            Long id, String numero, LocalDate dataInicio, LocalDate dataFim, String auditores,
            StatusAuditoria status, Long templateId, String formulario, EditorDto editor, int totalItens,
            int respondidos,
            Map<Conformidade, Long> contagem, Instant atualizadoEm) {
    }

    public record AnexoDto(UUID id, Long itemId, String nomeOriginal, String contentType, long tamanho,
            String sha256, String enviadoPor, Instant enviadoEm) {
        public static AnexoDto de(Anexo a) {
            return new AnexoDto(a.getId(), a.getItem().getId(), a.getNomeOriginal(), a.getContentType(),
                    a.getTamanho(), a.getSha256(), a.getEnviadoPor(), a.getEnviadoEm());
        }
    }

    public record ItemDto(
            Long id, int numero, String secao, String grupo, String clausula, String pergunta, String processo,
            String documentos, String orientacao, String responsavel, String local, String evidencias,
            Conformidade conformidade,
            String rnc, Instant atualizadoEm, String atualizadoPor, Instant alteradoEmCliente,
            List<AnexoDto> anexos, String alteradaNaRevisao) {
        static ItemDto de(ItemAuditoria i, List<AnexoDto> anexos) {
            return new ItemDto(i.getId(), i.getNumero(), i.getSecao(), i.getGrupo(), i.getClausula(),
                    i.getPergunta(), i.getProcesso(), i.getDocumentos(), i.getOrientacao(), i.getResponsavel(),
                    i.getLocal(),
                    i.getEvidencias(), i.getConformidade(), i.getRnc(), i.getAtualizadoEm(), i.getAtualizadoPor(),
                    i.getAlteradoEmCliente(), anexos, i.getAlteradaNaRevisao());
        }
    }

    /** Como o formulário se apresenta na tela (rótulos e campos usados). */
    public record FormularioDto(Long id, String nome, String titulo, String identificacao, String rotuloAuditoria,
            String rotuloReferencia, boolean usaProcesso, boolean usaDocumentos, boolean usaLocal,
            boolean exigeRegistroNc, String escopoPadrao, List<String> sugestoesEvidencia,
            List<TemplateChecklist.Opcao> opcoes, String rodape, String tipo) {
        public static FormularioDto de(TemplateChecklist t) {
            return new FormularioDto(t.getId(), t.getNome(), t.getTitulo(), t.getIdentificacao(),
                    t.getRotuloAuditoria(), t.getRotuloReferencia(), t.isUsaProcesso(), t.isUsaDocumentos(),
                    t.isUsaLocal(), t.isExigeRegistroNc(), t.getEscopoPadrao(), t.getSugestoesEvidencia(),
                    t.getOpcoes(), t.getRodape(), t.getTipo());
        }

        /** Auditoria concluída (ou reaberta) mostra a identificação, o título e o rodapé da revisão em que foi feita. */
        public static FormularioDto de(Auditoria a) {
            FormularioDto f = de(a.getTemplate());
            if (!a.isRevisaoCongelada()) {
                return f;
            }
            return new FormularioDto(f.id(), f.nome(), a.getSnapTitulo(), a.getSnapIdentificacao(),
                    f.rotuloAuditoria(), f.rotuloReferencia(), f.usaProcesso(), f.usaDocumentos(), f.usaLocal(),
                    f.exigeRegistroNc(), f.escopoPadrao(), f.sugestoesEvidencia(), f.opcoes(), a.getSnapRodape(), f.tipo());
        }
    }

    public record AuditoriaDetalhe(
            Long id, String numero, LocalDate dataInicio, LocalDate dataFim, String auditores, String escopo,
            StatusAuditoria status, String formulario, FormularioDto modelo, EditorDto editor, String criadoPor,
            Instant criadoEm, Instant atualizadoEm, Instant concluidoEm, List<ItemDto> itens,
            List<ItemRemovidoDto> removidos) {
    }

    /** Item cuja pergunta saiu numa revisão; aparece só na janela de removidas e no histórico. */
    public record ItemRemovidoDto(ItemDto item, String removidoNaRevisao, boolean visto) {
    }

    public record NovaAuditoriaReq(
            @NotBlank @Size(max = 30) String numero,
            @NotNull LocalDate dataInicio,
            LocalDate dataFim,
            @NotBlank @Size(max = 1000) String auditores,
            @Size(max = 4000) String escopo,
            Long templateId) {
    }

    public record CabecalhoReq(
            @NotBlank @Size(max = 30) String numero,
            @NotNull LocalDate dataInicio,
            LocalDate dataFim,
            @NotBlank @Size(max = 1000) String auditores,
            @Size(max = 4000) String escopo) {
    }

    public record ItemReq(
            @Size(max = 500) String processo,
            @Size(max = 2000) String documentos,
            @Size(max = 20000) String evidencias,
            Conformidade conformidade,
            @Size(max = 60) String rnc,
            @Size(max = 2000) String orientacao,
            @Size(max = 150) String responsavel,
            @Size(max = 200) String local,
            Instant alteradoEmCliente,
            /** Texto da pergunta que o aparelho mostrava ao responder (revisão de formulário). */
            @Size(max = 2000) String perguntaVista) {
    }

    public record HistoricoDto(Long id, Integer itemNumero, String evento, String campo, String valorAnterior,
            String valorNovo, String usuario, String dispositivo, Instant registradoEm) {
        public static HistoricoDto de(RegistroAlteracao r) {
            return new HistoricoDto(r.getId(), r.getItemNumero(), r.getEvento(), r.getCampo(), r.getValorAnterior(),
                    r.getValorNovo(), r.getUsuario(), r.getDispositivo(), r.getRegistradoEm());
        }
    }
}
