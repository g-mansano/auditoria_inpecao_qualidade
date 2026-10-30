package dev.mansano.auditoria;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONValue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import dev.mansano.auditoria.permissao.NivelAcesso;
import dev.mansano.auditoria.usuario.Papel;
import dev.mansano.auditoria.usuario.Usuario;
import dev.mansano.auditoria.usuario.UsuarioRepository;

/** Revisão de formulário pelo app. Banco próprio: as revisões mudam os formulários. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:revisao;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.armazenamento-dir=target/anexos-teste-revisao",
        "app.admin-inicial.senha=admin-teste-123" })
@AutoConfigureMockMvc
@DirtiesContext
class RevisaoFormularioTests {

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
        if (!usuarios.existsByUsernameIgnoreCase("rita")) {
            var rita = new Usuario("rita", "Rita Revisora", encoder.encode("senha-rita-123"), Papel.AUDITOR);
            rita.setPodeRevisar(true);
            usuarios.save(rita);
            var caio = usuarios.save(new Usuario("caio", "Caio Auditor", encoder.encode("senha-caio-123"), Papel.AUDITOR));
            Permissoes.auditorias(permissoes, templates, rita, NivelAcesso.PREENCHER);
            Permissoes.auditorias(permissoes, templates, caio, NivelAcesso.PREENCHER);
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
    void revisaoInicialEPermissoes() throws Exception {
        String admin = login("admin", "admin-teste-123");
        // ISO 9001 e ANVISA começam na Rev. 04.
        mvc.perform(get("/api/templates").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].formulario.identificacao").value(endsWith("Rev.04")))
                .andExpect(jsonPath("$[2].formulario.identificacao").value(endsWith("Rev.04")));
        mvc.perform(get("/api/auth/eu").header("Authorization", admin))
                .andExpect(jsonPath("$.podeRevisar").value(true));
        mvc.perform(get("/api/auth/eu").header("Authorization", login("rita", "senha-rita-123")))
                .andExpect(jsonPath("$.podeRevisar").value(true));
        mvc.perform(get("/api/auth/eu").header("Authorization", login("caio", "senha-caio-123")))
                .andExpect(jsonPath("$.podeRevisar").value(false));
    }

    // ------------------------------------------------------------------ auxiliares de revisão

    static final String DISP = "tab-caio";

    String templateId(String token, int indice) throws Exception {
        String json = mvc.perform(get("/api/templates").header("Authorization", token)).andReturn().getResponse()
                .getContentAsString();
        return String.valueOf((Integer) JsonPath.read(json, "$[" + indice + "].formulario.id"));
    }

    String revisao(String token, String tid) throws Exception {
        return mvc.perform(get("/api/templates/" + tid + "/revisao").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> perguntas(String revisaoJson) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Object o : (List<Object>) JsonPath.read(revisaoJson, "$.perguntas")) {
            lista.add(new LinkedHashMap<>((Map<String, Object>) o));
        }
        return lista;
    }

    static String corpo(String revisaoJson, boolean nova, List<Map<String, Object>> perguntas) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("nova", nova);
        b.put("versao", ((Number) JsonPath.read(revisaoJson, "$.versao")).longValue());
        b.put("titulo", JsonPath.read(revisaoJson, "$.titulo"));
        b.put("perguntas", perguntas);
        return JSONValue.toJSONString(b);
    }

    org.springframework.test.web.servlet.ResultActions salvar(String token, String tid, String corpo) throws Exception {
        return mvc.perform(put("/api/templates/" + tid + "/revisao").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    String criarAuditoria(String token, String tid, String numero) throws Exception {
        String json = mvc.perform(post("/api/auditorias").header("Authorization", token).header("X-Dispositivo", DISP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"" + numero + "\",\"dataInicio\":\"2026-10-01\",\"auditores\":\"Caio\",\"templateId\":"
                        + tid + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json;
    }

    org.springframework.test.web.servlet.ResultActions responder(String token, String aud, Object itemId, String corpo)
            throws Exception {
        return mvc.perform(put("/api/auditorias/" + aud + "/itens/" + itemId).header("Authorization", token)
                .header("X-Dispositivo", DISP).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    static String hoje() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    // ------------------------------------------------------------------ testes

    @Test
    void abrirNovaRevisaoAtualizaNumeroERodape() throws Exception {
        String rita = login("rita", "senha-rita-123");
        String hk = templateId(rita, 4);
        String r = revisao(rita, hk);
        Assertions.assertEquals("00", JsonPath.read(r, "$.revisao"));
        Assertions.assertEquals(15, perguntas(r).size());

        salvar(rita, hk, corpo(r, true, perguntas(r)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisao").value("01"))
                .andExpect(jsonPath("$.rodape").value("FQ-105 - Rev. 01 - Data: " + hoje()));
        mvc.perform(get("/api/templates").header("Authorization", rita))
                .andExpect(jsonPath("$[4].formulario.identificacao").value("FQ-105 Rev.01"))
                .andExpect(jsonPath("$[4].formulario.rodape").value("FQ-105 - Rev. 01 - Data: " + hoje()));

        // Esqueceu um detalhe: edita a Rev. 01, que continua Rev. 01.
        String r2 = revisao(rita, hk);
        var ps = perguntas(r2);
        ps.get(0).put("texto", ps.get(0).get("texto") + " e corredores");
        salvar(rita, hk, corpo(r2, false, ps)).andExpect(status().isOk()).andExpect(jsonPath("$.revisao").value("01"));
        Assertions.assertTrue(((String) JsonPath.read(revisao(rita, hk), "$.perguntas[0].texto")).endsWith("e corredores"));

        // Histórico de revisões: dois salvamentos, o mais recente primeiro, com a pergunta antes -> depois.
        mvc.perform(get("/api/templates/" + hk + "/revisoes").header("Authorization", rita))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].revisao").value("01"))
                .andExpect(jsonPath("$[0].nova").value(false))
                .andExpect(jsonPath("$[0].salvoPor").value("Rita Revisora"))
                .andExpect(jsonPath("$[0].detalhes[0].tipo").value("ALTERADA"))
                .andExpect(jsonPath("$[0].detalhes[0].antes").value("Piso e bancadas limpos, sem resíduos acumulados"))
                .andExpect(jsonPath("$[0].detalhes[0].depois").value("Piso e bancadas limpos, sem resíduos acumulados e corredores"))
                .andExpect(jsonPath("$[1].nova").value(true));
    }

    @Test
    void previaNaoGrava() throws Exception {
        String rita = login("rita", "senha-rita-123");
        String iso = templateId(rita, 0);
        String r = revisao(rita, iso);
        var ps = perguntas(r);
        String original = (String) ps.get(0).get("texto");
        ps.get(0).put("texto", original + " (rascunho)");
        mvc.perform(post("/api/templates/" + iso + "/revisao/previa").header("Authorization", rita)
                .contentType(MediaType.APPLICATION_JSON).content(corpo(r, true, ps)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisao").value("07"))
                .andExpect(jsonPath("$.alteradas").value(1));
        Assertions.assertEquals(original, JsonPath.read(revisao(rita, iso), "$.perguntas[0].texto"));
        Assertions.assertEquals("06", JsonPath.read(revisao(rita, iso), "$.revisao"));
    }

    @Test
    void auditoriaEmAndamentoRecebeRevisao() throws Exception {
        String rita = login("rita", "senha-rita-123");
        String caio = login("caio", "senha-caio-123");
        String inm = templateId(caio, 3);
        String aud = criarAuditoria(caio, inm, "INM-R");
        String audId = extrair(aud, "^\\{\"id\":(\\d+)");
        List<Integer> itens = JsonPath.read(aud, "$.itens[*].id");
        responder(caio, audId, itens.get(0),
                "{\"conformidade\":\"C\",\"evidencias\":\"ok\",\"alteradoEmCliente\":\"2026-01-01T10:00:00Z\"}")
                .andExpect(status().isOk());
        responder(caio, audId, itens.get(1), "{\"conformidade\":\"C\",\"alteradoEmCliente\":\"2026-01-01T10:00:00Z\"}")
                .andExpect(status().isOk());
        responder(caio, audId, itens.get(2),
                "{\"conformidade\":\"NC\",\"rnc\":\"RNC 099\",\"alteradoEmCliente\":\"2026-01-01T10:00:00Z\"}")
                .andExpect(status().isOk());

        String r = revisao(rita, inm);
        var ps = perguntas(r);
        String texto3 = (String) ps.get(2).get("texto");
        Assertions.assertEquals(1, ps.get(0).get("respondidas"));
        ps.get(0).put("texto", ps.get(0).get("texto") + " (revisado)");
        ps.get(1).put("texto", "  " + ps.get(1).get("texto") + "  "); // só espaços: não conta como alterada
        ps.remove(3); // sem resposta: sai de vez
        ps.remove(2); // respondida: vai para "removidos"
        Map<String, Object> nova = new LinkedHashMap<>();
        nova.put("secao", "CHECKLIST");
        nova.put("grupo", "PESSOAL");
        nova.put("texto", "Há treinamento registrado para o operador?");
        ps.add(nova);

        salvar(rita, inm, corpo(r, false, ps))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisao").value("01"))
                .andExpect(jsonPath("$.alteradas").value(1))
                .andExpect(jsonPath("$.novas").value(1))
                .andExpect(jsonPath("$.removidas").value(2))
                .andExpect(jsonPath("$.itensReabertos").value(1));

        mvc.perform(get("/api/auditorias/" + audId).header("Authorization", caio))
                .andExpect(jsonPath("$.itens", hasSize(11)))
                .andExpect(jsonPath("$.itens[0].conformidade").doesNotExist())
                .andExpect(jsonPath("$.itens[0].alteradaNaRevisao").value("01"))
                .andExpect(jsonPath("$.itens[0].evidencias").value("ok"))
                .andExpect(jsonPath("$.itens[0].pergunta").value(endsWith("(revisado)")))
                .andExpect(jsonPath("$.itens[1].conformidade").value("C"))
                .andExpect(jsonPath("$.itens[1].alteradaNaRevisao").doesNotExist())
                .andExpect(jsonPath("$.itens[2].numero").value(3))
                .andExpect(jsonPath("$.itens[10].pergunta").value("Há treinamento registrado para o operador?"))
                .andExpect(jsonPath("$.removidos", hasSize(1)))
                .andExpect(jsonPath("$.removidos[0].item.pergunta").value(texto3))
                .andExpect(jsonPath("$.removidos[0].item.rnc").value("RNC 099"))
                .andExpect(jsonPath("$.removidos[0].removidoNaRevisao").value("01"))
                .andExpect(jsonPath("$.removidos[0].visto").value(false));
        // Contagens da lista ignoram o removido.
        mvc.perform(get("/api/auditorias").header("Authorization", caio))
                .andExpect(jsonPath("$[?(@.numero == 'INM-R')].totalItens").value(11))
                .andExpect(jsonPath("$[?(@.numero == 'INM-R')].respondidos").value(1));

        // Envio atrasado (feito offline sobre o texto antigo): guarda o texto, mas a conformidade não vale.
        // Quem decide é o texto que o inspetor viu, não o relógio do aparelho (aqui um horário "no futuro").
        String textoAntigo = JSONValue.toJSONString(perguntas(r).get(0).get("texto"));
        responder(caio, audId, itens.get(0), "{\"conformidade\":\"C\",\"evidencias\":\"offline\",\"perguntaVista\":"
                + textoAntigo + ",\"alteradoEmCliente\":\"2030-01-01T11:00:00Z\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conformidade").doesNotExist())
                .andExpect(jsonPath("$.evidencias").value("offline"))
                .andExpect(jsonPath("$.alteradaNaRevisao").value("01"));
        // Item removido não aceita edição, mas o que chegou fica registrado no histórico.
        responder(caio, audId, itens.get(2), "{\"conformidade\":\"C\",\"evidencias\":\"vista tarde\","
                + "\"alteradoEmCliente\":\"2030-10-15T12:00:00Z\"}")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("ITEM_REMOVIDO"));
        // Respondeu de novo vendo o texto novo, mesmo com relógio atrasado: vale e o aviso some.
        String textoNovo = JSONValue.toJSONString(ps.get(0).get("texto"));
        responder(caio, audId, itens.get(0), "{\"conformidade\":\"C\",\"evidencias\":\"offline\",\"perguntaVista\":"
                + textoNovo + ",\"alteradoEmCliente\":\"2030-01-01T12:00:00Z\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conformidade").value("C"))
                .andExpect(jsonPath("$.alteradaNaRevisao").doesNotExist());
        mvc.perform(post("/api/auditorias/" + audId + "/removidos/vistos").header("Authorization", caio)
                .header("X-Dispositivo", DISP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removidos[0].visto").value(true));
        mvc.perform(get("/api/auditorias/" + audId + "/historico").header("Authorization", caio))
                .andExpect(jsonPath("$[?(@.evento == 'REVISAO_APLICADA')]").isNotEmpty())
                // Renumeração fica no histórico (o histórico antigo cita o número de então).
                .andExpect(jsonPath("$[?(@.evento == 'REVISAO_APLICADA' && @.campo == 'numero' && @.valorAnterior == '5' && @.valorNovo == '3')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.valorNovo =~ /.*vista tarde.*/)]").isNotEmpty());
    }

    @Test
    void removerPerguntaComAnexoExcluidoNaoQuebra() throws Exception {
        String rita = login("rita", "senha-rita-123");
        String caio = login("caio", "senha-caio-123");
        String iso = templateId(caio, 0);
        String aud = criarAuditoria(caio, iso, "13485-ANX");
        String audId = extrair(aud, "^\\{\"id\":(\\d+)");
        List<Integer> itens = JsonPath.read(aud, "$.itens[*].id");
        Integer ultimo = itens.get(itens.size() - 1);
        String anexoId = java.util.UUID.randomUUID().toString();
        mvc.perform(multipart("/api/auditorias/" + audId + "/itens/" + ultimo + "/anexos")
                .file(new org.springframework.mock.web.MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", new byte[] { 1, 2, 3 }))
                .param("id", anexoId).header("Authorization", caio).header("X-Dispositivo", DISP))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/anexos/" + anexoId).header("Authorization", caio).header("X-Dispositivo", DISP))
                .andExpect(status().isNoContent());

        String r = revisao(rita, iso);
        var ps = perguntas(r);
        ps.remove(ps.size() - 1);
        salvar(rita, iso, corpo(r, false, ps)).andExpect(status().isOk());
        mvc.perform(get("/api/auditorias/" + audId).header("Authorization", caio))
                .andExpect(jsonPath("$.itens", hasSize(24)))
                .andExpect(jsonPath("$.removidos", hasSize(0)));
        // Mesmo sem resposta, o item é mantido (remoção lógica): edição atrasada recebe 410, não 404.
        responder(caio, audId, ultimo, "{\"conformidade\":\"C\"}").andExpect(status().isGone());
    }

    @Test
    void adminRebaixadoNaoFicaComPermissaoDeRevisar() throws Exception {
        String admin = login("admin", "admin-teste-123");
        var zeca = usuarios.save(new Usuario("zeca", "Zeca Admin", encoder.encode("senha-zeca-123"), Papel.ADMIN));
        // Editar um admin com a chave ligada não grava a permissão: admin revisa por ser admin.
        mvc.perform(put("/api/usuarios/" + zeca.getId()).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Zeca Admin\",\"papel\":\"ADMIN\",\"ativo\":true,\"podeRevisar\":true}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/usuarios/" + zeca.getId()).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Zeca Admin\",\"papel\":\"AUDITOR\",\"ativo\":true,\"podeRevisar\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/eu").header("Authorization", login("zeca", "senha-zeca-123")))
                .andExpect(jsonPath("$.papel").value("AUDITOR"))
                .andExpect(jsonPath("$.podeRevisar").value(false));
        // A lista do admin mostra o valor gravado (falso para admin), para a chave não nascer ligada.
        mvc.perform(get("/api/usuarios").header("Authorization", admin))
                .andExpect(jsonPath("$[?(@.username == 'admin')].podeRevisar").value(false));
    }

    @Test
    void concluidaEReabertaNaoMudam() throws Exception {
        String rita = login("rita", "senha-rita-123");
        String caio = login("caio", "senha-caio-123");
        String admin = login("admin", "admin-teste-123");
        String iso9001 = templateId(caio, 2);
        String a = criarAuditoria(caio, iso9001, "9001-A");
        String aId = extrair(a, "^\\{\"id\":(\\d+)");
        String primeiraPergunta = JsonPath.read(a, "$.itens[0].pergunta");
        for (Integer item : JsonPath.<List<Integer>>read(a, "$.itens[*].id")) {
            responder(caio, aId, item, "{\"conformidade\":\"C\"}").andExpect(status().isOk());
        }
        mvc.perform(post("/api/auditorias/" + aId + "/concluir").header("Authorization", caio).header("X-Dispositivo", DISP))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auditorias/" + aId + "/reabrir").header("Authorization", admin).header("X-Dispositivo", "pc"))
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
        String bId = extrair(criarAuditoria(caio, iso9001, "9001-B"), "^\\{\"id\":(\\d+)");

        String r = revisao(rita, iso9001);
        Assertions.assertEquals("04", JsonPath.read(r, "$.revisao"));
        var ps = perguntas(r);
        ps.get(0).put("texto", "Texto novo da Rev. 05");
        ps.remove(1);
        salvar(rita, iso9001, corpo(r, true, ps)).andExpect(jsonPath("$.revisao").value("05"));

        mvc.perform(get("/api/auditorias/" + aId).header("Authorization", caio))
                .andExpect(jsonPath("$.formulario").value("FQ-103 Rev.04"))
                .andExpect(jsonPath("$.modelo.rodape").value("FQ-103 - Rev. 04 - Data: 15/01/2026"))
                .andExpect(jsonPath("$.itens", hasSize(20)))
                .andExpect(jsonPath("$.itens[0].pergunta").value(primeiraPergunta))
                .andExpect(jsonPath("$.itens[0].conformidade").value("C"));
        mvc.perform(get("/api/auditorias/" + bId).header("Authorization", caio))
                .andExpect(jsonPath("$.formulario").value("FQ-103 Rev.05"))
                .andExpect(jsonPath("$.modelo.rodape").value("FQ-103 - Rev. 05 - Data: " + hoje()))
                .andExpect(jsonPath("$.itens", hasSize(19)))
                .andExpect(jsonPath("$.itens[0].pergunta").value("Texto novo da Rev. 05"));
    }

    @Test
    void semPermissaoRecebe403() throws Exception {
        String caio = login("caio", "senha-caio-123");
        String rita = login("rita", "senha-rita-123");
        String iso = templateId(caio, 0);
        mvc.perform(get("/api/templates/" + iso + "/revisao").header("Authorization", caio))
                .andExpect(status().isForbidden());
        String r = revisao(rita, iso);
        salvar(caio, iso, corpo(r, true, perguntas(r))).andExpect(status().isForbidden());
    }

    @Test
    void versaoVelhaRecebe409() throws Exception {
        String rita = login("rita", "senha-rita-123");
        String anvisa = templateId(rita, 1);
        String r = revisao(rita, anvisa);
        salvar(rita, anvisa, corpo(r, false, perguntas(r))).andExpect(status().isOk());
        // Clique duplo / outro revisor: mesmo corpo, versão já usada.
        salvar(rita, anvisa, corpo(r, true, perguntas(r)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REVISAO_DESATUALIZADA"))
                .andExpect(jsonPath("$.mensagem").value(containsString("Rita Revisora")));
        Assertions.assertEquals("04", JsonPath.read(revisao(rita, anvisa), "$.revisao"));
    }

    @Test
    void formularioSemPerguntasRecebe400() throws Exception {
        String rita = login("rita", "senha-rita-123");
        String iso = templateId(rita, 0);
        String r = revisao(rita, iso);
        salvar(rita, iso, corpo(r, true, List.of())).andExpect(status().isBadRequest());
        // Pergunta sem texto também é recusada.
        var ps = perguntas(r);
        ps.get(0).put("texto", "   ");
        salvar(rita, iso, corpo(r, true, ps)).andExpect(status().isBadRequest());
    }
}
