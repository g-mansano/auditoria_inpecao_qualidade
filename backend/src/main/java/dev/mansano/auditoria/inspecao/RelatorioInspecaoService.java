package dev.mansano.auditoria.inspecao;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import dev.mansano.auditoria.seguranca.UsuarioLogado;

/**
 * PDF do dia para o PCP: resumo, ocorrências (N/C e EPI N/P) com foto, grade igual ao FQ-106
 * (OPs nas colunas, tópicos nas linhas), lotes e anexo de fotos. Rodapé do formulário em cada folha.
 * A fonte padrão do PDF não tem o símbolo ✓: "Conforme" sai como "C".
 */
@Service
public class RelatorioInspecaoService {

    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));
    private static final int OPS_POR_TABELA = 8;
    private static final int LADO_FOTO = 700;

    private final InspecaoService inspecoes;

    public RelatorioInspecaoService(InspecaoService inspecoes) {
        this.inspecoes = inspecoes;
    }

    public record Relatorio(String nomeArquivo, byte[] pdf) {
    }

    public Relatorio gerar(String diaId, UsuarioLogado eu) {
        InspecaoDia d = inspecoes.paraRelatorio(diaId, eu);
        Map<Long, List<AnexoInspecao>> fotos = new HashMap<>();
        for (AnexoInspecao a : inspecoes.anexosAtivos(diaId)) {
            fotos.computeIfAbsent(a.getItem().getId(), k -> new ArrayList<>()).add(a);
        }
        Map<String, String> imagens = new HashMap<>();
        fotos.values().stream().flatMap(List::stream).forEach(a -> {
            String uri = miniatura(a);
            if (uri != null) {
                imagens.put(a.getId(), uri);
            }
        });
        String html = html(d, fotos, imagens);
        try (var out = new ByteArrayOutputStream()) {
            var b = new PdfRendererBuilder();
            b.useFastMode();
            b.withHtmlContent(html, null);
            b.toStream(out);
            b.run();
            String nome = "Inspecao OPs " + d.getData().format(DateTimeFormatter.ISO_DATE) + " - "
                    + d.getInspetor().getNome() + ".pdf";
            return new Relatorio(nome, out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o PDF", e);
        }
    }

    // ---------------------------------------------------------------- HTML

    private String html(InspecaoDia d, Map<Long, List<AnexoInspecao>> fotos, Map<String, String> imagens) {
        List<InspecaoOp> ops = d.getOpsAtivas();
        StringBuilder h = new StringBuilder(8192);
        h.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><html><head><style>")
                .append("@page { size: A4 landscape; margin: 12mm 10mm 16mm 10mm;")
                .append(" @bottom-center { content: \"").append(css(d.getTemplate().getRodape())).append("\";")
                .append(" font-family: Helvetica; font-size: 8pt; color: #475467; }")
                .append(" @bottom-right { content: \"Página \" counter(page) \" de \" counter(pages);")
                .append(" font-family: Helvetica; font-size: 8pt; color: #475467; } }")
                .append("body { font-family: Helvetica; font-size: 8.5pt; color: #0f172a; }")
                .append("h1 { font-size: 14pt; color: #1e2958; margin: 0 0 2mm; }")
                .append("h2 { font-size: 11pt; color: #1e2958; margin: 5mm 0 2mm; border-bottom: 1px solid #cbd5e1; }")
                .append("table { border-collapse: collapse; width: 100%; }")
                .append("td, th { border: 1px solid #94a3b8; padding: 1.2mm 1.5mm; vertical-align: top; }")
                .append("th { background: #e2e8f0; text-align: left; }")
                .append(".grade td.v { text-align: center; font-weight: bold; }")
                .append(".NC, .N, .P { background: #fee2e2; color: #b91c1c; }")
                .append(".C, .S { color: #15803d; }")
                .append(".meta td { border: 0; padding: 0 4mm 1mm 0; }")
                .append(".resumo td { text-align: center; } .ocorr img, .anexo img { width: 45mm; margin: 1mm; }")
                .append(".mudo { color: #64748b; } .quebra { page-break-before: always; }")
                .append("</style></head><body>");

        // Cabeçalho
        h.append("<h1>").append(esc(d.getTemplate().getIdentificacao())).append(" · Inspeção de OPs</h1><table class=\"meta\"><tr>")
                .append("<td><b>Data:</b> ").append(esc(FMT_DATA.format(d.getData()))).append("</td>")
                .append("<td><b>Inspetor:</b> ").append(esc(d.getInspetor().getNome())).append("</td>")
                .append("<td><b>Situação:</b> ")
                .append(d.getStatus() == StatusInspecao.FINALIZADA
                        ? "Finalizada em " + esc(FMT_HORA.format(d.getFinalizadaEm()))
                        : "Em andamento (ainda não finalizada)")
                .append("</td><td><b>OPs inspecionadas:</b> ").append(ops.size()).append("</td></tr></table>");

        // Resumo das siglas
        Map<String, Long> contagem = new LinkedHashMap<>();
        InspecaoValores.TOPICO.keySet().forEach(k -> contagem.put("T" + k, 0L));
        InspecaoValores.EPI.keySet().forEach(k -> contagem.put("E" + k, 0L));
        long brancos = 0;
        for (InspecaoOp o : ops) {
            for (InspecaoItem i : o.getItens()) {
                if (i.getValor() == null) {
                    brancos++;
                    continue;
                }
                contagem.merge(("EPI".equals(i.getEscala()) ? "E" : "T") + i.getValor(), 1L, Long::sum);
            }
        }
        h.append("<h2>Resumo do dia</h2><table class=\"resumo\"><tr>");
        InspecaoValores.TOPICO.forEach((k, v) -> h.append("<th>").append(esc(siglaPdf(null, k))).append(" · ")
                .append(esc(v[1])).append("</th>"));
        InspecaoValores.EPI.forEach((k, v) -> h.append("<th>EPI ").append(esc(v[0])).append(" · ").append(esc(v[1]))
                .append("</th>"));
        h.append("<th>Em branco</th></tr><tr>");
        InspecaoValores.TOPICO.keySet().forEach(k -> h.append("<td>").append(contagem.get("T" + k)).append("</td>"));
        InspecaoValores.EPI.keySet().forEach(k -> h.append("<td>").append(contagem.get("E" + k)).append("</td>"));
        h.append("<td>").append(brancos).append("</td></tr></table>");

        // Ocorrências para o PCP
        h.append("<h2>Não conformidades e ocorrências</h2>");
        StringBuilder linhas = new StringBuilder();
        for (InspecaoOp o : ops) {
            for (InspecaoItem i : o.getItens()) {
                if (!InspecaoValores.naoConforme(i.getEscala(), i.getValor())) {
                    continue;
                }
                linhas.append("<tr><td>").append(esc(o.getNumeroOp())).append("</td><td>")
                        .append(esc(o.getCodigoProduto())).append("</td><td>").append(esc(i.getTopico()))
                        .append("</td><td class=\"v ").append(sanitizarClasseCss(i.getValor())).append("\">")
                        .append(esc(siglaPdf(i.getEscala(), i.getValor()))).append("</td><td>")
                        .append(esc(i.getObservacao())).append("</td><td class=\"ocorr\">");
                for (AnexoInspecao a : fotos.getOrDefault(i.getId(), List.of())) {
                    String uri = imagens.get(a.getId());
                    linhas.append(uri != null ? "<img src=\"" + uri + "\"/>" : "<span class=\"mudo\">" + esc(a.getNomeOriginal()) + "</span> ");
                }
                linhas.append("</td></tr>");
            }
        }
        if (linhas.isEmpty()) {
            h.append("<p>Nenhuma não conformidade registrada no dia.</p>");
        } else {
            h.append("<table><tr><th>Nº OP</th><th>Código do produto</th><th>Tópico</th><th>Resultado</th>")
                    .append("<th>Observação</th><th>Fotos</th></tr>").append(linhas).append("</table>");
        }

        // Grade igual à folha
        for (int ini = 0; ini < Math.max(ops.size(), 1); ini += OPS_POR_TABELA) {
            List<InspecaoOp> bloco = ops.subList(Math.min(ini, ops.size()), Math.min(ini + OPS_POR_TABELA, ops.size()));
            h.append("<h2").append(ini == 0 ? " class=\"quebra\"" : "").append(">Inspeções OP's")
                    .append(ops.size() > OPS_POR_TABELA ? " (" + (ini + 1) + "–" + (ini + bloco.size()) + ")" : "")
                    .append("</h2><table class=\"grade\">");
            linha(h, "Nº OP", bloco, o -> esc(o.getNumeroOp()), null);
            linha(h, "Código do produto", bloco, o -> esc(o.getCodigoProduto()), null);
            List<InspecaoItem> modelo = bloco.isEmpty() ? List.of() : bloco.get(0).getItens();
            for (InspecaoItem m : modelo) {
                int n = m.getNumero();
                linha(h, m.getTopico(), bloco, o -> {
                    InspecaoItem i = item(o, n);
                    return i == null ? "" : esc(siglaPdf(i.getEscala(), i.getValor()));
                }, o -> {
                    InspecaoItem i = item(o, n);
                    return i == null || i.getValor() == null ? "v" : "v " + sanitizarClasseCss(i.getValor());
                });
                if ("EPI".equals(m.getEscala())) {
                    linha(h, "Observações EPI", bloco, o -> {
                        InspecaoItem i = item(o, n);
                        return i == null ? "" : esc(i.getObservacao());
                    }, null);
                }
            }
            linha(h, "Lotes do insumo", bloco, o -> {
                StringBuilder s = new StringBuilder();
                o.getLotesAtivos().forEach(l -> s.append(esc(l.getLote() == null ? "(sem lote)" : l.getLote()))
                        .append(l.getDataFabricacao() == null ? "" : " · fab. " + esc(l.getDataFabricacao()))
                        .append("<br/>"));
                return s.toString();
            }, null);
            h.append("</table>");
        }
        // Sem o símbolo de check: a fonte padrão do PDF não tem esse glifo.
        h.append("<p class=\"mudo\">Legenda: C - Conforme (o check da folha) · N/A - Não se Aplica · ")
                .append("N/C - Não Conforme · N/V - Não Visível · N/I - Não Inspecionado · N/P - Não Presente · ")
                .append("EPI: S - Sim, N - Não, P - Parcial · célula vazia: não preenchido</p>");

        // Observações e fotos dos demais tópicos
        StringBuilder obs = new StringBuilder();
        for (InspecaoOp o : ops) {
            for (InspecaoItem i : o.getItens()) {
                List<AnexoInspecao> fs = fotos.getOrDefault(i.getId(), List.of());
                boolean ocorrencia = InspecaoValores.naoConforme(i.getEscala(), i.getValor());
                if (ocorrencia || (i.getObservacao() == null && fs.isEmpty()) || "EPI".equals(i.getEscala()) && fs.isEmpty()) {
                    continue;
                }
                obs.append("<tr><td>").append(esc(o.getNumeroOp())).append("</td><td>").append(esc(i.getTopico()))
                        .append("</td><td class=\"v ").append(sanitizarClasseCss(i.getValor())).append("\">")
                        .append(esc(siglaPdf(i.getEscala(), i.getValor()))).append("</td><td>")
                        .append(esc(i.getObservacao())).append("</td><td class=\"anexo\">");
                for (AnexoInspecao a : fs) {
                    String uri = imagens.get(a.getId());
                    obs.append(uri != null ? "<img src=\"" + uri + "\"/>" : "<span class=\"mudo\">" + esc(a.getNomeOriginal()) + "</span> ");
                }
                obs.append("</td></tr>");
            }
        }
        if (!obs.isEmpty()) {
            h.append("<h2 class=\"quebra\">Observações e fotos dos demais tópicos</h2><table><tr><th>Nº OP</th>")
                    .append("<th>Tópico</th><th>Resultado</th><th>Observação</th><th>Fotos</th></tr>").append(obs)
                    .append("</table>");
        }
        h.append("</body></html>");
        return h.toString();
    }

    private static void linha(StringBuilder h, String rotulo, List<InspecaoOp> ops,
            java.util.function.Function<InspecaoOp, String> valor,
            java.util.function.Function<InspecaoOp, String> classe) {
        h.append("<tr><th>").append(esc(rotulo)).append("</th>");
        for (InspecaoOp o : ops) {
            String cls = classe == null ? null : sanitizarClasseCss(classe.apply(o));
            h.append("<td").append(cls == null ? "" : " class=\"" + cls + "\"").append(">")
                    .append(valor.apply(o)).append("</td>");
        }
        h.append("</tr>");
    }

    private static InspecaoItem item(InspecaoOp o, int numero) {
        return o.getItens().stream().filter(i -> i.getNumero() == numero).findFirst().orElse(null);
    }

    /** Sigla no PDF: igual à folha, exceto o ✓ (sem glifo na fonte padrão), que vira "C". */
    private static String siglaPdf(String escala, String valor) {
        if (valor == null) {
            return "";
        }
        return "C".equals(valor) && !"EPI".equals(escala) ? "C" : InspecaoValores.sigla(escala, valor);
    }

    // ---------------------------------------------------------------- imagens

    /** Foto reduzida (máx. 700 px, JPEG) em data URI; HEIC ou arquivo que não é imagem: nulo (sai só o nome). */
    private String miniatura(AnexoInspecao a) {
        if (!a.getContentType().startsWith("image/") || a.getContentType().equals("image/heic")) {
            return null;
        }
        try {
            Path p = inspecoes.caminho(a);
            BufferedImage img = ImageIO.read(p.toFile());
            if (img == null) {
                return null;
            }
            double f = Math.min(1.0, (double) LADO_FOTO / Math.max(img.getWidth(), img.getHeight()));
            int w = Math.max(1, (int) (img.getWidth() * f));
            int h = Math.max(1, (int) (img.getHeight() * f));
            BufferedImage r = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = r.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(img, 0, 0, w, h, null);
            g.dispose();
            var out = new ByteArrayOutputStream();
            ImageIO.write(r, "jpg", out);
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return null; // arquivo ausente ou ilegível: o relatório sai assim mesmo, com o nome
        }
    }

    // ---------------------------------------------------------------- escapes

    private static String sanitizarClasseCss(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[^a-zA-Z0-9_ -]", "").trim();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                case '\n' -> b.append("<br/>");
                case '✓' -> b.append("C");
                default -> {
                    if (c >= 0x20 || c == '\t') {
                        b.append(c);
                    }
                }
            }
        }
        return b.toString();
    }

    private static String css(String s) {
        // Vai dentro de <style> de um XHTML: escapa as aspas/quebras do CSS e os caracteres especiais do XML.
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace('\r', ' ').replace('\n', ' ')
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
