package dev.mansano.auditoria.permissao;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.mansano.auditoria.comum.NegocioException;
import dev.mansano.auditoria.seguranca.UsuarioLogado;
import dev.mansano.auditoria.template.TemplateChecklist;
import dev.mansano.auditoria.template.TemplateRepository;
import dev.mansano.auditoria.usuario.Papel;
import dev.mansano.auditoria.usuario.UsuarioRepository;

/**
 * Quem vê e quem preenche cada formulário. O admin acessa tudo; os demais, só o que o admin liberou
 * (tabela permissao_formulario). Papel LEITOR fica no máximo em VER.
 */
@Service
public class PermissaoService {

    private final PermissaoRepository repo;
    private final RegistroPermissaoRepository registros;
    private final TemplateRepository templates;
    private final UsuarioRepository usuarios;

    public PermissaoService(PermissaoRepository repo, RegistroPermissaoRepository registros,
            TemplateRepository templates, UsuarioRepository usuarios) {
        this.repo = repo;
        this.registros = registros;
        this.templates = templates;
        this.usuarios = usuarios;
    }

    public record PermissaoDto(Long templateId, String nome, String identificacao, String tipo, NivelAcesso nivel) {
    }

    public record PermissaoReq(Long templateId, NivelAcesso nivel) {
    }

    public record RegistroPermissaoDto(Long templateId, String formulario, NivelAcesso nivelAnterior,
            NivelAcesso nivelNovo, String alteradoPor, java.time.Instant alteradoEm) {
    }

    // ---------------------------------------------------------------- consulta para as regras

    /** Nível efetivo do usuário logado no formulário; vazio = sem acesso. */
    @Transactional(readOnly = true)
    public Optional<NivelAcesso> nivel(UsuarioLogado eu, Long templateId) {
        if (eu.isAdmin()) {
            return Optional.of(NivelAcesso.PREENCHER);
        }
        return repo.findById(new PermissaoFormulario.Chave(eu.id(), templateId))
                .map(p -> limitar(eu.papel(), p.getNivel()));
    }

    /** Formulários acessíveis ao usuário logado, com o nível de cada um. */
    @Transactional(readOnly = true)
    public Map<Long, NivelAcesso> mapa(UsuarioLogado eu) {
        Map<Long, NivelAcesso> m = new HashMap<>();
        if (eu.isAdmin()) {
            templates.findAll().forEach(t -> m.put(t.getId(), NivelAcesso.PREENCHER));
            return m;
        }
        repo.doUsuario(eu.id()).forEach(p -> m.put(p.getId().templateId(), limitar(eu.papel(), p.getNivel())));
        return m;
    }

    public void exigirVer(UsuarioLogado eu, Long templateId) {
        if (nivel(eu, templateId).isEmpty()) {
            throw NegocioException.proibido("Você não tem acesso a este formulário.");
        }
    }

    public void exigirPreencher(UsuarioLogado eu, Long templateId) {
        if (!nivel(eu, templateId).map(n -> n == NivelAcesso.PREENCHER).orElse(false)) {
            throw NegocioException.proibido("Seu acesso a este formulário é só de consulta.");
        }
    }

    // ---------------------------------------------------------------- administração

    /** Todos os formulários ativos, com o nível gravado do usuário (nulo = nenhum). */
    @Transactional(readOnly = true)
    public List<PermissaoDto> doUsuario(Long usuarioId) {
        Map<Long, NivelAcesso> gravados = new HashMap<>();
        repo.doUsuario(usuarioId).forEach(p -> gravados.put(p.getId().templateId(), p.getNivel()));
        return templates.findByAtivoTrueOrderByOrdemAscCodigoAsc().stream()
                .map(t -> new PermissaoDto(t.getId(), t.getNome(), t.getIdentificacao(), t.getTipo(),
                        gravados.get(t.getId())))
                .toList();
    }

    /** Grava os níveis (nulo = retira o acesso) e registra cada mudança. */
    @Transactional
    public List<PermissaoDto> definir(Long usuarioId, List<PermissaoReq> novas, UsuarioLogado eu) {
        var alvo = usuarios.findById(usuarioId).orElseThrow(() -> NegocioException.naoEncontrado("Usuário"));
        String por = usuarios.findById(eu.id()).map(u -> u.getNome()).orElse(eu.username());
        Map<Long, PermissaoFormulario> atuais = new LinkedHashMap<>();
        repo.doUsuario(usuarioId).forEach(p -> atuais.put(p.getId().templateId(), p));
        Map<Long, NivelAcesso> porFormulario = new LinkedHashMap<>();
        novas.forEach(r -> porFormulario.put(r.templateId(), r.nivel()));
        for (var e : porFormulario.entrySet()) {
            var r = new PermissaoReq(e.getKey(), e.getValue());
            TemplateChecklist t = templates.findById(r.templateId())
                    .orElseThrow(() -> NegocioException.naoEncontrado("Formulário"));
            NivelAcesso novo = r.nivel();
            if (novo == NivelAcesso.PREENCHER && alvo.getPapel() == Papel.LEITOR) {
                throw NegocioException.invalido("Usuário leitor só pode ter acesso de consulta (Ver).");
            }
            PermissaoFormulario atual = atuais.get(t.getId());
            NivelAcesso antes = atual == null ? null : atual.getNivel();
            if (Objects.equals(antes, novo)) {
                continue;
            }
            if (novo == null) {
                repo.delete(atual);
            } else if (atual == null) {
                repo.save(new PermissaoFormulario(usuarioId, t.getId(), novo));
            } else {
                atual.setNivel(novo);
            }
            registros.save(new RegistroPermissao(usuarioId, t.getId(), antes, novo, por));
        }
        repo.flush();
        return doUsuario(usuarioId);
    }

    @Transactional(readOnly = true)
    public List<RegistroPermissaoDto> historico(Long usuarioId) {
        Map<Long, String> nomes = new HashMap<>();
        templates.findAll().forEach(t -> nomes.put(t.getId(), t.getNome()));
        return registros.findByUsuarioIdOrderByAlteradoEmDescIdDesc(usuarioId).stream()
                .map(r -> new RegistroPermissaoDto(r.getTemplateId(), nomes.get(r.getTemplateId()),
                        r.getNivelAnterior(), r.getNivelNovo(), r.getAlteradoPor(), r.getAlteradoEm()))
                .toList();
    }

    private static NivelAcesso limitar(Papel papel, NivelAcesso n) {
        return papel == Papel.LEITOR ? NivelAcesso.VER : n;
    }
}
