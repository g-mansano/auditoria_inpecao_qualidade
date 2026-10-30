package dev.mansano.auditoria.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import dev.mansano.auditoria.auditoria.AuditoriaDtos.AuditoriaDetalhe;
import dev.mansano.auditoria.auditoria.AuditoriaDtos.ItemDto;
import dev.mansano.auditoria.auditoria.AuditoriaDtos.ItemReq;
import dev.mansano.auditoria.auditoria.AuditoriaDtos.NovaAuditoriaReq;
import dev.mansano.auditoria.auditoria.AuditoriaService;
import dev.mansano.auditoria.auditoria.Conformidade;
import dev.mansano.auditoria.config.AppProperties;
import dev.mansano.auditoria.inspecao.InspecaoDtos;
import dev.mansano.auditoria.inspecao.InspecaoDtos.LoteReq;
import dev.mansano.auditoria.inspecao.InspecaoDtos.NovoDiaReq;
import dev.mansano.auditoria.inspecao.InspecaoDtos.OpReq;
import dev.mansano.auditoria.inspecao.InspecaoService;
import dev.mansano.auditoria.permissao.NivelAcesso;
import dev.mansano.auditoria.permissao.PermissaoService;
import dev.mansano.auditoria.permissao.PermissaoService.PermissaoReq;
import dev.mansano.auditoria.seguranca.UsuarioLogado;
import dev.mansano.auditoria.template.TemplateChecklist;
import dev.mansano.auditoria.template.TemplateRepository;
import dev.mansano.auditoria.usuario.InicializadorAdmin;
import dev.mansano.auditoria.usuario.Usuario;
import dev.mansano.auditoria.usuario.UsuarioRepository;

/**
 * Popula a demonstração: contas de exemplo, permissões e alguns registros prontos (uma auditoria concluída,
 * duas em andamento e uma inspeção finalizada), criados pelos próprios serviços para gerar o histórico real.
 * Uma vez por dia o banco volta ao estado inicial (Flyway clean + migrate) e é populado de novo.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "app.demo.ativo", havingValue = "true")
public class DadosDemo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DadosDemo.class);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final String APARELHO = "demo-dados-iniciais";

    private final AppProperties props;
    private final UsuarioRepository usuarios;
    private final TemplateRepository templates;
    private final PasswordEncoder encoder;
    private final PermissaoService permissoes;
    private final AuditoriaService auditorias;
    private final InspecaoService inspecoes;
    private final InicializadorAdmin inicializadorAdmin;
    private final Flyway flyway;

    public DadosDemo(AppProperties props, UsuarioRepository usuarios, TemplateRepository templates,
            PasswordEncoder encoder, PermissaoService permissoes, AuditoriaService auditorias,
            InspecaoService inspecoes, InicializadorAdmin inicializadorAdmin, Flyway flyway) {
        this.props = props;
        this.usuarios = usuarios;
        this.templates = templates;
        this.encoder = encoder;
        this.permissoes = permissoes;
        this.auditorias = auditorias;
        this.inspecoes = inspecoes;
        this.inicializadorAdmin = inicializadorAdmin;
        this.flyway = flyway;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarios.existsByUsernameIgnoreCase(ContasDemo.AUDITOR.username())) {
            return; // banco persistente já populado
        }
        popular();
    }

    /** Volta ao estado inicial: apaga tudo (inclusive sessões e anexos) e popula de novo. */
    @Scheduled(cron = "${app.demo.reset-cron}", zone = "America/Sao_Paulo")
    public synchronized void resetar() {
        log.warn("Demonstração: reiniciando o banco e os anexos.");
        flyway.clean();
        flyway.migrate();
        apagarAnexos();
        inicializadorAdmin.run(null);
        popular();
    }

    void popular() {
        UsuarioLogado admin = UsuarioLogado.de(usuarios.findByUsernameIgnoreCase(ContasDemo.ADMIN.username()).orElseThrow());
        Usuario ana = criar(ContasDemo.AUDITOR, true);
        Usuario caio = criar(ContasDemo.INSPETOR, false);
        Usuario lia = criar(ContasDemo.LEITOR, false);

        List<TemplateChecklist> forms = templates.findByAtivoTrueOrderByOrdemAscCodigoAsc();
        TemplateChecklist inspecao = form(forms, "FQ-106");
        permissoes.definir(ana.getId(), forms.stream().filter(t -> t != inspecao)
                .map(t -> new PermissaoReq(t.getId(), NivelAcesso.PREENCHER)).toList(), admin);
        permissoes.definir(caio.getId(), List.of(new PermissaoReq(inspecao.getId(), NivelAcesso.PREENCHER)), admin);
        permissoes.definir(lia.getId(), forms.stream()
                .map(t -> new PermissaoReq(t.getId(), NivelAcesso.VER)).toList(), admin);

        LocalDate hoje = LocalDate.now(FUSO);
        UsuarioLogado eu = UsuarioLogado.de(ana);
        auditoriaConcluida(eu, form(forms, "FQ-101"), hoje);
        auditoriaEmAndamento(eu, form(forms, "FQ-103"), hoje);
        housekeeping(eu, form(forms, "FQ-105"), hoje);
        inspecaoFinalizada(UsuarioLogado.de(caio), inspecao, hoje.minusDays(1));
        log.info("Demonstração populada: {} contas de exemplo.", ContasDemo.TODAS.size());
    }

    // ---------------------------------------------------------------- usuários

    private Usuario criar(ContasDemo.Conta c, boolean podeRevisar) {
        var u = new Usuario(c.username(), c.nome(), encoder.encode(props.demo().senha()), c.papel());
        u.setPodeRevisar(podeRevisar);
        return usuarios.save(u);
    }

    private static TemplateChecklist form(List<TemplateChecklist> forms, String codigo) {
        return forms.stream().filter(t -> t.getCodigo().equals(codigo)).findFirst().orElseThrow();
    }

    // ---------------------------------------------------------------- auditorias

    private void auditoriaConcluida(UsuarioLogado eu, TemplateChecklist t, LocalDate hoje) {
        AuditoriaDetalhe a = auditorias.criar(new NovaAuditoriaReq("AI-01/" + hoje.getYear(), hoje.minusDays(21),
                hoje.minusDays(20), "Ana Auditora\nBruno Lopes", t.getEscopoPadrao(), t.getId()), eu, APARELHO);
        for (ItemDto i : a.itens()) {
            switch (i.numero()) {
                case 12 -> responder(a, i, eu, Conformidade.NC, "RNC 014/" + hoje.getYear(),
                        "Dois operadores da linha 2 sem registro do treinamento na instrução de trabalho revisada.");
                case 21 -> responder(a, i, eu, Conformidade.OM, null,
                        "Etiquetas de calibração legíveis, mas sem padrão de cor por vencimento.");
                case 4 -> responder(a, i, eu, Conformidade.NA, null, "Sem família de produto nova no período.");
                default -> responder(a, i, eu, Conformidade.C, null, "Evidência verificada por amostragem.");
            }
        }
        auditorias.concluir(a.id(), eu, APARELHO);
    }

    private void auditoriaEmAndamento(UsuarioLogado eu, TemplateChecklist t, LocalDate hoje) {
        AuditoriaDetalhe a = auditorias.criar(new NovaAuditoriaReq("AI-02/" + hoje.getYear(), hoje.minusDays(2), null,
                "Ana Auditora", "ISO 9001:2015. Processos de produção e suprimentos.", t.getId()), eu, APARELHO);
        for (ItemDto i : a.itens().subList(0, 8)) {
            if (i.numero() == 6) {
                responder(a, i, eu, Conformidade.NC, "NC 031/" + hoje.getYear(),
                        "Matriz de riscos sem revisão desde o ano anterior.");
            } else {
                responder(a, i, eu, Conformidade.C, null, "Conforme.");
            }
        }
        auditorias.checkin(a.id(), eu, APARELHO); // livre para o visitante assumir
    }

    private void housekeeping(UsuarioLogado eu, TemplateChecklist t, LocalDate hoje) {
        AuditoriaDetalhe a = auditorias.criar(new NovaAuditoriaReq("HK-05/" + hoje.getYear(), hoje, null,
                "Ana Auditora", null, t.getId()), eu, APARELHO);
        for (ItemDto i : a.itens().subList(0, 6)) {
            if (i.numero() == 2) {
                responderLocal(a, i, eu, Conformidade.NC, "Almoxarifado", "Vazamento de óleo sob a empilhadeira 3.");
            } else if (i.numero() == 4) {
                responderLocal(a, i, eu, Conformidade.NC, "Manutenção", "Chaves fora do quadro de sombra.");
            } else {
                responderLocal(a, i, eu, Conformidade.C, null, null);
            }
        }
        auditorias.checkin(a.id(), eu, APARELHO);
    }

    private void responder(AuditoriaDetalhe a, ItemDto i, UsuarioLogado eu, Conformidade c, String rnc, String evid) {
        auditorias.atualizarItem(a.id(), i.id(), new ItemReq(i.processo(), i.documentos(), evid, c, rnc,
                i.orientacao(), i.responsavel(), i.local(), Instant.now(), i.pergunta()), eu, APARELHO);
    }

    private void responderLocal(AuditoriaDetalhe a, ItemDto i, UsuarioLogado eu, Conformidade c, String local,
            String evid) {
        auditorias.atualizarItem(a.id(), i.id(), new ItemReq(i.processo(), i.documentos(), evid, c, null,
                i.orientacao(), i.responsavel(), local, Instant.now(), i.pergunta()), eu, APARELHO);
    }

    // ---------------------------------------------------------------- inspeção de OPs

    private void inspecaoFinalizada(UsuarioLogado eu, TemplateChecklist t, LocalDate dia) {
        String id = UUID.randomUUID().toString();
        inspecoes.abrirDia(id, new NovoDiaReq(t.getId(), dia), eu, APARELHO);
        inspecoes.salvarOp(id, UUID.randomUUID().toString(), new OpReq(1, "OP-1042", "PA-2201", List.of(
                item(1, "C", null), item(2, "C", null), item(3, "C", null), item(4, "C", null),
                item(5, "C", null), item(6, "C", "Média de 10 unidades: 501 g"), item(7, "S", null),
                item(8, "C", null), item(9, "NA", null), item(10, "C", null)),
                List.of(new LoteReq(UUID.randomUUID().toString(), "MP-7781", "03/" + dia.getYear())),
                Instant.now()), eu, APARELHO);
        inspecoes.salvarOp(id, UUID.randomUUID().toString(), new OpReq(2, "OP-1043", "PA-2215", List.of(
                item(1, "C", null), item(2, "NC", "Rótulo sem a data de validade"), item(3, "C", null),
                item(4, "C", null), item(5, "C", null), item(6, "C", null),
                item(7, "P", "Operador sem protetor auricular"), item(8, "C", null), item(9, "C", null),
                item(10, "NI", null)),
                List.of(new LoteReq(UUID.randomUUID().toString(), "MP-7790", "04/" + dia.getYear())),
                Instant.now()), eu, APARELHO);
        inspecoes.finalizar(id, eu, APARELHO);
    }

    private static InspecaoDtos.ItemReq item(int numero, String valor, String obs) {
        return new InspecaoDtos.ItemReq(numero, valor, obs, null, null);
    }

    // ---------------------------------------------------------------- anexos

    private void apagarAnexos() {
        Path dir = Path.of(props.armazenamentoDir());
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(dir)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Não apagou {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Não limpou os anexos: {}", e.getMessage());
        }
    }
}
