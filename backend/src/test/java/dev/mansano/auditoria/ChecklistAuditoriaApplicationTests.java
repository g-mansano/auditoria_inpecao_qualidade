package dev.mansano.auditoria;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import dev.mansano.auditoria.permissao.NivelAcesso;
import dev.mansano.auditoria.usuario.Papel;
import dev.mansano.auditoria.usuario.Usuario;
import dev.mansano.auditoria.usuario.UsuarioRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.armazenamento-dir=target/anexos-teste",
        "app.admin-inicial.senha=admin-teste-123" })
@AutoConfigureMockMvc
@DirtiesContext
class ChecklistAuditoriaApplicationTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    dev.mansano.auditoria.permissao.PermissaoRepository permissoes;
    @Autowired
    dev.mansano.auditoria.template.TemplateRepository templates;

    @BeforeEach
    void usuarios() {
        if (!usuarios.existsByUsernameIgnoreCase("ana")) {
            var ana = usuarios.save(new Usuario("ana", "Ana Auditora", encoder.encode("senha-ana-123"), Papel.AUDITOR));
            var beto = usuarios.save(new Usuario("beto", "Beto Auditor", encoder.encode("senha-beto-123"), Papel.AUDITOR));
            var leo = usuarios.save(new Usuario("leo", "Leo Leitor", encoder.encode("senha-leo-123"), Papel.LEITOR));
            Permissoes.auditorias(permissoes, templates, ana, NivelAcesso.PREENCHER);
            Permissoes.auditorias(permissoes, templates, beto, NivelAcesso.PREENCHER);
            Permissoes.auditorias(permissoes, templates, leo, NivelAcesso.VER);
        }
    }

    String login(String user, String senha) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + extrair(json, "\"token\":\"([^\"]+)\"");
    }

    static String extrair(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        Assertions.assertTrue(m.find(), "não achou " + regex + " em " + json);
        return m.group(1);
    }

    @Test
    void semTokenRecebe401() throws Exception {
        mvc.perform(get("/api/auditorias")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginInvalido() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ana\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fluxoCompletoComEdicaoExclusiva() throws Exception {
        String ana = login("ana", "senha-ana-123");
        String beto = login("beto", "senha-beto-123");
        String leo = login("leo", "senha-leo-123");
        String admin = login("admin", "admin-teste-123");

        // Leitor não cria auditoria.
        mvc.perform(post("/api/auditorias").header("Authorization", leo).header("X-Dispositivo", "tab-leo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"999\",\"dataInicio\":\"2026-10-01\",\"auditores\":\"Leo\"}"))
                .andExpect(status().isForbidden());

        // Ana cria: 25 itens do FQ-101 Rev.06, já em check-out no tablet dela.
        String criada = mvc.perform(post("/api/auditorias").header("Authorization", ana)
                .header("X-Dispositivo", "tab-ana").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"002\",\"dataInicio\":\"2026-10-13\",\"dataFim\":\"2026-10-14\","
                        + "\"auditores\":\"Ana Auditora\",\"escopo\":\"ISO 13485:2016\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itens", hasSize(25)))
                .andExpect(jsonPath("$.formulario").value("FQ-101 Rev.06"))
                .andExpect(jsonPath("$.editor.nome").value("Ana Auditora"))
                .andExpect(jsonPath("$.itens[0].processo").value("PG-01 - Gestão da Qualidade"))
                .andReturn().getResponse().getContentAsString();
        String id = extrair(criada, "^\\{\"id\":(\\d+)");
        String item1 = extrair(criada, "\"itens\":\\[\\{\"id\":(\\d+)");

        String corpoItem = "{\"evidencias\":\"Manual revisado\",\"conformidade\":\"NC\",\"rnc\":\"014\","
                + "\"alteradoEmCliente\":\"2026-10-13T12:00:00Z\"}";

        // Beto não consegue editar nem pegar o check-out.
        mvc.perform(put("/api/auditorias/" + id + "/itens/" + item1).header("Authorization", beto)
                .header("X-Dispositivo", "cel-beto").contentType(MediaType.APPLICATION_JSON).content(corpoItem))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.mensagem", containsString("Ana Auditora")));
        mvc.perform(post("/api/auditorias/" + id + "/checkout").header("Authorization", beto)
                .header("X-Dispositivo", "cel-beto"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auditorias/" + id + "/checkout?forcar=true").header("Authorization", beto)
                .header("X-Dispositivo", "cel-beto"))
                .andExpect(status().isConflict());

        // A própria Ana, em outro aparelho, também não edita.
        mvc.perform(put("/api/auditorias/" + id + "/itens/" + item1).header("Authorization", ana)
                .header("X-Dispositivo", "cel-ana").contentType(MediaType.APPLICATION_JSON).content(corpoItem))
                .andExpect(status().isLocked());

        // Ana edita no tablet.
        mvc.perform(put("/api/auditorias/" + id + "/itens/" + item1).header("Authorization", ana)
                .header("X-Dispositivo", "tab-ana").contentType(MediaType.APPLICATION_JSON).content(corpoItem))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conformidade").value("NC"))
                .andExpect(jsonPath("$.rnc").value("014"));

        // Envio atrasado da fila (mais antigo) não sobrescreve.
        mvc.perform(put("/api/auditorias/" + id + "/itens/" + item1).header("Authorization", ana)
                .header("X-Dispositivo", "tab-ana").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conformidade\":\"C\",\"alteradoEmCliente\":\"2026-10-13T11:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conformidade").value("NC"));

        // Anexo: upload idempotente pelo UUID gerado no aparelho.
        UUID anexoId = UUID.randomUUID();
        var foto = new MockMultipartFile("arquivo", "foto linha 1.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
        for (int i = 0; i < 2; i++) {
            mvc.perform(multipart("/api/auditorias/" + id + "/itens/" + item1 + "/anexos").file(foto)
                    .param("id", anexoId.toString()).header("Authorization", ana).header("X-Dispositivo", "tab-ana"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.contentType").value("image/jpeg"));
        }
        var exe = new MockMultipartFile("arquivo", "virus.exe", "application/octet-stream", new byte[] { 1 });
        mvc.perform(multipart("/api/auditorias/" + id + "/itens/" + item1 + "/anexos").file(exe)
                .param("id", UUID.randomUUID().toString()).header("Authorization", ana)
                .header("X-Dispositivo", "tab-ana"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/anexos/" + anexoId).header("Authorization", beto))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        // Concluir com itens pendentes falha.
        mvc.perform(post("/api/auditorias/" + id + "/concluir").header("Authorization", ana)
                .header("X-Dispositivo", "tab-ana"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("sem conformidade")));

        // Ana libera; Beto pega o check-out.
        mvc.perform(post("/api/auditorias/" + id + "/checkin").header("Authorization", ana)
                .header("X-Dispositivo", "tab-ana"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.editor").doesNotExist());
        mvc.perform(post("/api/auditorias/" + id + "/checkout").header("Authorization", beto)
                .header("X-Dispositivo", "cel-beto"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.editor.nome").value("Beto Auditor"));

        // Admin força a liberação.
        mvc.perform(post("/api/auditorias/" + id + "/checkout?forcar=true").header("Authorization", admin)
                .header("X-Dispositivo", "pc-admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.editor.nome").value("Administrador"));

        // Histórico registra tudo.
        mvc.perform(get("/api/auditorias/" + id + "/historico").header("Authorization", leo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.evento == 'CHECKOUT_FORCADO')]").exists())
                .andExpect(jsonPath("$[?(@.campo == 'conformidade' && @.valorNovo == 'NC')]").exists())
                .andExpect(jsonPath("$[?(@.evento == 'ANEXO_INCLUIDO')]", hasSize(1)));

        // A exportação para Excel/ZIP foi removida (decisão do usuário): fica só a impressão.
        mvc.perform(get("/api/auditorias/" + id + "/exportar.xlsx").header("Authorization", leo))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/auditorias/" + id + "/exportar.zip").header("Authorization", leo))
                .andExpect(status().isNotFound());
    }

    @Test
    void checklistAnvisaComRoteiroDeCampoEExclusao() throws Exception {
        String ana = login("ana", "senha-ana-123");
        String beto = login("beto", "senha-beto-123");

        String templates = mvc.perform(get("/api/templates").header("Authorization", ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].formulario.nome").value("ISO 13485"))
                .andExpect(jsonPath("$[1].formulario.nome").value("ANVISA"))
                .andExpect(jsonPath("$[1].totalPerguntas").value(24))
                .andReturn().getResponse().getContentAsString();
        String anvisaId = extrair(templates, "\"formulario\":\\{\"id\":(\\d+),\"nome\":\"ANVISA\"");

        String criada = mvc.perform(post("/api/auditorias").header("Authorization", ana)
                .header("X-Dispositivo", "tab-ana").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"15/2026\",\"dataInicio\":\"2026-02-05\",\"auditores\":\"Ana\",\"templateId\":"
                        + anvisaId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itens", hasSize(24)))
                .andExpect(jsonPath("$.modelo.rotuloReferencia").value("Capítulo / Seção"))
                .andExpect(jsonPath("$.modelo.usaProcesso").value(false))
                .andExpect(jsonPath("$.itens[0].grupo").value("Requisitos gerais"))
                .andExpect(jsonPath("$.itens[0].clausula").value("Cap. II, seção I"))
                .andExpect(jsonPath("$.itens[0].orientacao").value("Documentação"))
                .andExpect(jsonPath("$.itens[16].secao").value("CAMPO"))
                .andExpect(jsonPath("$.itens[16].grupo").value("Almoxarifado"))
                .andExpect(jsonPath("$.itens[16].responsavel").value("Líder do almoxarifado"))
                .andReturn().getResponse().getContentAsString();
        String id = extrair(criada, "^\\{\"id\":(\\d+)");
        String item1 = extrair(criada, "\"itens\":\\[\\{\"id\":(\\d+)");

        mvc.perform(put("/api/auditorias/" + id + "/itens/" + item1).header("Authorization", ana)
                .header("X-Dispositivo", "tab-ana").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conformidade\":\"NC\",\"rnc\":\"NC 031/26\",\"orientacao\":\"Visual\","
                        + "\"alteradoEmCliente\":\"2026-02-05T12:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orientacao").value("Visual"));

        // Beto não criou: não pode excluir. Ana pode.
        mvc.perform(delete("/api/auditorias/" + id).header("Authorization", beto).header("X-Dispositivo", "cel-beto"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/auditorias/" + id).header("Authorization", ana).header("X-Dispositivo", "tab-ana"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auditorias/" + id).header("Authorization", ana)).andExpect(status().isNotFound());
        mvc.perform(get("/api/auditorias").header("Authorization", ana))
                .andExpect(jsonPath("$[?(@.numero == '15/2026')]").doesNotExist());
    }

    @Test
    void formulariosIso9001HousekeepingEInmetro() throws Exception {
        String ana = login("ana", "senha-ana-123");
        String templates = mvc.perform(get("/api/templates").header("Authorization", ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[2].formulario.nome").value("ISO 9001"))
                .andExpect(jsonPath("$[2].totalPerguntas").value(20))
                .andExpect(jsonPath("$[3].formulario.nome").value("INMETRO"))
                .andExpect(jsonPath("$[3].totalPerguntas").value(12))
                .andExpect(jsonPath("$[3].formulario.opcoes[0].sigla").value("S"))
                .andExpect(jsonPath("$[4].formulario.nome").value("Housekeeping"))
                .andExpect(jsonPath("$[4].formulario.rotuloAuditoria").value("Auditoria Housekeeping"))
                .andExpect(jsonPath("$[4].formulario.usaLocal").value(true))
                // Rodapé de cada formulário (sai em cada folha impressa).
                .andExpect(jsonPath("$[0].formulario.rodape").value("FQ-101 - Rev. 06 - Data: 02/03/2026"))
                .andExpect(jsonPath("$[1].formulario.rodape").value("FQ-102 - Rev. 04 - Data: 10/02/2026"))
                .andExpect(jsonPath("$[2].formulario.rodape").value("FQ-103 - Rev. 04 - Data: 15/01/2026"))
                .andExpect(jsonPath("$[3].formulario.rodape").value("FQ-104 - Rev.: 01 - Data: 20/04/2026"))
                .andExpect(jsonPath("$[4].formulario.rodape").value("FQ-105 - Rev. 00 - Data: 05/01/2026"))
                .andReturn().getResponse().getContentAsString();
        String inmetroId = extrair(templates, "\"formulario\":\\{\"id\":(\\d+),\"nome\":\"INMETRO\"");
        String hkId = extrair(templates, "\"formulario\":\\{\"id\":(\\d+),\"nome\":\"Housekeeping\"");

        // INMETRO: escala S/N/NA; OM não existe nesse formulário.
        String inm = mvc.perform(post("/api/auditorias").header("Authorization", ana).header("X-Dispositivo", "tab-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"I-1\",\"dataInicio\":\"2026-08-13\",\"auditores\":\"Ana\",\"templateId\":" + inmetroId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itens[0].pergunta").value("A empresa possui área de recebimento separada da expedição?"))
                .andExpect(jsonPath("$.itens[0].grupo").value("INSTALAÇÕES"))
                .andReturn().getResponse().getContentAsString();
        String inmId = extrair(inm, "^\\{\"id\":(\\d+)");
        String inmItem = extrair(inm, "\"itens\":\\[\\{\"id\":(\\d+)");
        mvc.perform(put("/api/auditorias/" + inmId + "/itens/" + inmItem).header("Authorization", ana)
                .header("X-Dispositivo", "tab-ana").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conformidade\":\"OM\",\"alteradoEmCliente\":\"2026-08-13T10:00:00Z\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/auditorias/" + inmId + "/itens/" + inmItem).header("Authorization", ana)
                .header("X-Dispositivo", "tab-ana").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conformidade\":\"C\",\"alteradoEmCliente\":\"2026-08-13T10:00:00Z\"}"))
                .andExpect(status().isOk());

        // Housekeeping: campo Local; NC não exige nº de registro para concluir.
        String hk = mvc.perform(post("/api/auditorias").header("Authorization", ana).header("X-Dispositivo", "tab-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"H-1\",\"dataInicio\":\"2026-02-12\",\"auditores\":\"Ana\",\"templateId\":" + hkId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itens", hasSize(15)))
                .andExpect(jsonPath("$.itens[0].grupo").value("LIMPEZA"))
                .andReturn().getResponse().getContentAsString();
        String hkAud = extrair(hk, "^\\{\"id\":(\\d+)");
        Matcher ids = Pattern.compile("\\{\"id\":(\\d+),\"numero\":\\d+,\"secao\"").matcher(hk);
        boolean primeiro = true;
        while (ids.find()) {
            String corpo = primeiro
                    ? "{\"conformidade\":\"NC\",\"local\":\"Almoxarifado\",\"alteradoEmCliente\":\"2026-02-12T10:00:00Z\"}"
                    : "{\"conformidade\":\"C\",\"alteradoEmCliente\":\"2026-02-12T10:00:00Z\"}";
            mvc.perform(put("/api/auditorias/" + hkAud + "/itens/" + ids.group(1)).header("Authorization", ana)
                    .header("X-Dispositivo", "tab-ana").contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isOk());
            primeiro = false;
        }
        mvc.perform(post("/api/auditorias/" + hkAud + "/concluir").header("Authorization", ana)
                .header("X-Dispositivo", "tab-ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.itens[0].local").value("Almoxarifado"));
    }

    @Test
    void arquivoInexistenteDa404EmVezDoIndex() throws Exception {
        mvc.perform(get("/media/nao-existe.woff2")).andExpect(status().isNotFound());
    }

    @Test
    void rotaDoAngularDevolveIndex() throws Exception {
        // Só faz sentido com o front compilado embutido (build.ps1).
        Assumptions.assumeTrue(new ClassPathResource("static/index.html").exists());
        mvc.perform(get("/auditorias/5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<app-root>")));
    }
}
