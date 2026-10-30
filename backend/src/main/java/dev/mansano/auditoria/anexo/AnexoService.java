package dev.mansano.auditoria.anexo;

import static dev.mansano.auditoria.historico.HistoricoService.ANEXO_EXCLUIDO;
import static dev.mansano.auditoria.historico.HistoricoService.ANEXO_INCLUIDO;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import dev.mansano.auditoria.auditoria.Auditoria;
import dev.mansano.auditoria.auditoria.AuditoriaDtos.AnexoDto;
import dev.mansano.auditoria.auditoria.AuditoriaService;
import dev.mansano.auditoria.auditoria.ItemAuditoria;
import dev.mansano.auditoria.comum.NegocioException;
import dev.mansano.auditoria.config.AppProperties;
import dev.mansano.auditoria.historico.HistoricoService;
import dev.mansano.auditoria.permissao.PermissaoService;
import dev.mansano.auditoria.seguranca.UsuarioLogado;

@Service
public class AnexoService {

    /** Content-Type decidido pela extensão, nunca pelo que o aparelho informa. */
    private static final Map<String, String> TIPOS = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("png", "image/png"),
            Map.entry("webp", "image/webp"), Map.entry("gif", "image/gif"), Map.entry("heic", "image/heic"),
            Map.entry("pdf", "application/pdf"), Map.entry("txt", "text/plain"), Map.entry("csv", "text/csv"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            Map.entry("ods", "application/vnd.oasis.opendocument.spreadsheet"),
            Map.entry("mp4", "video/mp4"), Map.entry("mov", "video/quicktime"));

    private final AnexoRepository repo;
    private final AuditoriaService auditorias;
    private final HistoricoService historico;
    private final AppProperties props;
    private final PermissaoService permissoes;
    private final Path raiz;

    public AnexoService(AnexoRepository repo, AuditoriaService auditorias, HistoricoService historico,
            AppProperties props, PermissaoService permissoes) throws IOException {
        this.permissoes = permissoes;
        this.repo = repo;
        this.auditorias = auditorias;
        this.historico = historico;
        this.props = props;
        this.raiz = Path.of(props.armazenamentoDir()).toAbsolutePath().normalize();
        Files.createDirectories(raiz);
    }

    public record Conteudo(Anexo anexo, Path arquivo) {
    }

    @Transactional
    public AnexoDto incluir(Long auditoriaId, Long itemId, UUID id, MultipartFile arquivo, UsuarioLogado eu,
            String dispositivo) {
        // Reenvio do mesmo anexo (rede caiu depois do upload): devolve o que já existe.
        var existente = repo.findById(id);
        if (existente.isPresent()) {
            permissoes.exigirVer(eu, existente.get().getItem().getAuditoria().getTemplate().getId());
            return AnexoDto.de(existente.get());
        }
        Auditoria a = auditorias.buscar(auditoriaId);
        auditorias.exigirCheckout(a, eu, dispositivo);
        ItemAuditoria item = a.getItens().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> NegocioException.naoEncontrado("Item"));

        ArquivoGravado g = gravar(String.valueOf(auditoriaId), id, arquivo);
        Anexo anexo = repo.save(new Anexo(id, item, g.nome(), g.contentType(), g.tamanho(), g.sha256(), g.caminho(),
                eu.username()));
        historico.evento(a.getId(), item.getNumero(), ANEXO_INCLUIDO, g.nome() + " (sha256 " + g.sha256() + ")",
                eu.username(), dispositivo);
        a.tocar();
        return AnexoDto.de(anexo);
    }

    /** Arquivo já gravado no disco: nome seguro, tipo pela extensão, tamanho, SHA-256 e caminho relativo. */
    public record ArquivoGravado(String nome, String contentType, long tamanho, String sha256, String caminho) {
    }

    /**
     * Grava o arquivo em {@code pasta/id.ext} dentro do armazenamento, validando a extensão pela whitelist.
     * Usado pelos anexos das auditorias e das inspeções.
     */
    public ArquivoGravado gravar(String pasta, UUID id, MultipartFile arquivo) {
        String nome = nomeSeguro(arquivo.getOriginalFilename());
        String ext = extensao(nome);
        if (!props.extensoesPermitidas().contains(ext) || !TIPOS.containsKey(ext)) {
            throw NegocioException.invalido("Tipo de arquivo não permitido: ." + ext);
        }
        if (arquivo.isEmpty()) {
            throw NegocioException.invalido("Arquivo vazio.");
        }
        Path relativo = Path.of(pasta, id + "." + ext);
        Path destino = raiz.resolve(relativo).normalize();
        if (!destino.startsWith(raiz)) {
            throw NegocioException.invalido("Caminho inválido.");
        }
        String sha;
        try {
            Files.createDirectories(destino.getParent());
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(arquivo.getInputStream(), md)) {
                Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
            }
            sha = HexFormat.of().formatHex(md.digest());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar anexo", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return new ArquivoGravado(nome, TIPOS.get(ext), arquivo.getSize(), sha, relativo.toString().replace('\\', '/'));
    }

    /** Caminho absoluto de um arquivo do armazenamento; recusa caminhos fora dele. */
    public Path resolver(String caminhoRelativo) {
        Path arquivo = raiz.resolve(caminhoRelativo).normalize();
        if (!arquivo.startsWith(raiz) || !Files.exists(arquivo)) {
            throw NegocioException.naoEncontrado("Arquivo do anexo");
        }
        return arquivo;
    }

    /** Exclusão lógica: o arquivo continua no disco e no banco, só some da tela. */
    @Transactional
    public void excluir(UUID id, UsuarioLogado eu, String dispositivo) {
        Anexo anexo = repo.findById(id).orElseThrow(() -> NegocioException.naoEncontrado("Anexo"));
        if (anexo.isExcluido()) {
            return;
        }
        Auditoria a = anexo.getItem().getAuditoria();
        auditorias.exigirCheckout(a, eu, dispositivo);
        anexo.excluir(eu.username());
        historico.evento(a.getId(), anexo.getItem().getNumero(), ANEXO_EXCLUIDO, anexo.getNomeOriginal(),
                eu.username(), dispositivo);
        a.tocar();
    }

    @Transactional(readOnly = true)
    public Conteudo abrir(UUID id, UsuarioLogado eu) {
        Anexo anexo = repo.findById(id).orElseThrow(() -> NegocioException.naoEncontrado("Anexo"));
        permissoes.exigirVer(eu, anexo.getItem().getAuditoria().getTemplate().getId());
        Path arquivo = raiz.resolve(anexo.getCaminho()).normalize();
        if (!arquivo.startsWith(raiz) || !Files.exists(arquivo)) {
            throw NegocioException.naoEncontrado("Arquivo do anexo");
        }
        return new Conteudo(anexo, arquivo);
    }

    public Path caminhoAbsoluto(Anexo anexo) {
        return raiz.resolve(anexo.getCaminho()).normalize();
    }

    static String nomeSeguro(String original) {
        String n = original == null ? "arquivo" : original;
        n = n.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        n = n.replaceAll("[\\p{Cntrl}\"<>|:*?]", "_").strip();
        if (n.isEmpty()) {
            n = "arquivo";
        }
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    static String extensao(String nome) {
        int p = nome.lastIndexOf('.');
        return p < 0 ? "" : nome.substring(p + 1).toLowerCase(Locale.ROOT);
    }
}
