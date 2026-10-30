package dev.mansano.auditoria;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assertions;
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

import com.jayway.jsonpath.JsonPath;

import dev.mansano.auditoria.permissao.NivelAcesso;
import dev.mansano.auditoria.permissao.PermissaoRepository;
import dev.mansano.auditoria.template.TemplateChecklist;
import dev.mansano.auditoria.template.TemplateRepository;
import dev.mansano.auditoria.usuario.Papel;
import dev.mansano.auditoria.usuario.Usuario;
import dev.mansano.auditoria.usuario.UsuarioRepository;

/** FQ-106 (inspeção de OPs) e permissões por formulário. Banco próprio. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:inspecao;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.armazenamento-dir=target/anexos-teste-inspecao",
        "app.admin-inicial.senha=admin-teste-123" })
@AutoConfigureMockMvc
@DirtiesContext
class InspecaoTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    PermissaoRepository permissoes;
    @Autowired
    TemplateRepository templates;

    static final String DISP = "cel-pedro";

    @BeforeEach
    void usuarios() {
        if (usuarios.existsByUsernameIgnoreCase("pedro")) {
            return;
        }
        TemplateChecklist fInsp = formInspecao();
        var pedro = usuarios.save(new Usuario("pedro", "Pedro Inspetor", encoder.encode("senha-pedro-123"), Papel.AUDITOR));
        Permissoes.formulario(permissoes, pedro, fInsp, NivelAcesso.PREENCHER);
        var lara = usuarios.save(new Usuario("lara", "Lara Inspetora", encoder.encode("senha-lara-123"), Papel.AUDITOR));
        Permissoes.formulario(permissoes, lara, fInsp, NivelAcesso.PREENCHER);
        var dani = usuarios.save(new Usuario("dani", "Dani Auditor", encoder.encode("senha-dani-123"), Papel.AUDITOR));
        Permissoes.auditorias(permissoes, templates, dani, NivelAcesso.PREENCHER);
    }

    TemplateChecklist formInspecao() {
        return templates.findAll().stream().filter(TemplateChecklist::isInspecaoOp).findFirst().orElseThrow();
    }

    String login(String user, String senha) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(json);
        Assertions.assertTrue(m.find());
        return "Bearer " + m.group(1);
    }

    org.springframework.test.web.servlet.ResultActions abrirDia(String token, String id, String data) throws Exception {
        return mvc.perform(put("/api/inspecoes/" + id).header("Authorization", token).header("X-Dispositivo", DISP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\":" + formInspecao().getId() + ",\"data\":\"" + data + "\"}"));
    }

    org.springframework.test.web.servlet.ResultActions salvarOp(String token, String dia, String op, String corpo)
            throws Exception {
        return mvc.perform(put("/api/inspecoes/" + dia + "/ops/" + op).header("Authorization", token)
                .header("X-Dispositivo", DISP).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    static String uuid() {
        return UUID.randomUUID().toString();
    }

    // ------------------------------------------------------------------ permissões

    @Test
    void pedroSoEnxergaOFormularioDeInspecao() throws Exception {
        String pedro = login("pedro", "senha-pedro-123");
        String admin = login("admin", "admin-teste-123");
        mvc.perform(get("/api/templates").header("Authorization", pedro))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].formulario.tipo").value("INSPECAO_OP"))
                .andExpect(jsonPath("$[0].formulario.rodape").value("FQ-106 - Rev. 01 - Data: 12/05/2026"))
                .andExpect(jsonPath("$[0].totalPerguntas").value(10))
                .andExpect(jsonPath("$[0].nivel").value("PREENCHER"));
        // Admin vê os 6 formulários.
        mvc.perform(get("/api/templates").header("Authorization", admin)).andExpect(jsonPath("$", hasSize(6)));

        // Auditoria criada pelo admin: o Pedro não lista, não abre e não cria (mesmo chamando a API direto).
        Long iso = templates.findAll().stream().filter(t -> !t.isInspecaoOp()).findFirst().orElseThrow().getId();
        String aud = mvc.perform(post("/api/auditorias").header("Authorization", admin).header("X-Dispositivo", "pc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"A-1\",\"dataInicio\":\"2026-10-01\",\"auditores\":\"Adm\",\"templateId\":" + iso + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer audId = JsonPath.read(aud, "$.id");
        mvc.perform(get("/api/auditorias").header("Authorization", pedro)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/auditorias/" + audId).header("Authorization", pedro)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auditorias/" + audId + "/historico").header("Authorization", pedro))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auditorias").header("Authorization", pedro).header("X-Dispositivo", DISP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"K-1\",\"dataInicio\":\"2026-10-01\",\"auditores\":\"K\",\"templateId\":" + iso + "}"))
                .andExpect(status().isForbidden());
        // E o Dani (auditor) não enxerga o formulário de inspeção.
        String dani = login("dani", "senha-dani-123");
        mvc.perform(get("/api/templates").header("Authorization", dani)).andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void adminLiberaEVerSoConsulta() throws Exception {
        String admin = login("admin", "admin-teste-123");
        String pedro = login("pedro", "senha-pedro-123");
        Long daniId = usuarios.findByUsernameIgnoreCase("dani").orElseThrow().getId();
        Long fInsp = formInspecao().getId();

        String dia = uuid();
        abrirDia(pedro, dia, "2026-10-02").andExpect(status().isOk());

        // Admin libera "Ver" da inspeção para o Dani.
        mvc.perform(put("/api/usuarios/" + daniId + "/permissoes").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("[{\"templateId\":" + fInsp + ",\"nivel\":\"VER\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.templateId == " + fInsp + ")].nivel").value("VER"));
        String dani = login("dani", "senha-dani-123");
        mvc.perform(get("/api/templates").header("Authorization", dani))
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[?(@.formulario.id == " + fInsp + ")].nivel").value("VER"));
        mvc.perform(get("/api/inspecoes/" + dia).header("Authorization", dani)).andExpect(status().isOk());
        mvc.perform(get("/api/inspecoes").header("Authorization", dani))
                .andExpect(jsonPath("$[?(@.id == '" + dia + "')]").isNotEmpty());
        // Ver não preenche.
        abrirDia(dani, uuid(), "2026-10-02").andExpect(status().isForbidden());
        salvarOp(dani, dia, uuid(), "{\"ordem\":1,\"numeroOp\":\"X\"}").andExpect(status().isForbidden());

        // Histórico da permissão; retirar o acesso.
        mvc.perform(get("/api/usuarios/" + daniId + "/permissoes/historico").header("Authorization", admin))
                .andExpect(jsonPath("$[0].nivelNovo").value("VER"))
                .andExpect(jsonPath("$[0].alteradoPor").value("Administrador"));
        mvc.perform(put("/api/usuarios/" + daniId + "/permissoes").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("[{\"templateId\":" + fInsp + ",\"nivel\":null}]"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/inspecoes/" + dia).header("Authorization", dani)).andExpect(status().isForbidden());
        // Só o admin administra permissões.
        mvc.perform(get("/api/usuarios/" + daniId + "/permissoes").header("Authorization", pedro))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ inspeção do dia

    @Test
    void fluxoCompletoDaInspecao() throws Exception {
        String pedro = login("pedro", "senha-pedro-123");
        String lara = login("lara", "senha-lara-123");
        String admin = login("admin", "admin-teste-123");
        String dia = uuid();
        abrirDia(pedro, dia, "2026-10-21")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.inspetor.nome").value("Pedro Inspetor"))
                .andExpect(jsonPath("$.topicos", hasSize(10)))
                .andExpect(jsonPath("$.topicos[6].texto").value("EPI's"))
                .andExpect(jsonPath("$.topicos[6].escala").value("EPI"));
        // Reenvio do mesmo id (fila offline): a mesma inspeção.
        abrirDia(pedro, dia, "2026-10-21").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(dia));
        // Outra inspeção no mesmo dia para o mesmo inspetor: recusada.
        abrirDia(pedro, uuid(), "2026-10-21").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("INSPECAO_EXISTENTE"));

        String op = uuid();
        String lote = uuid();
        String corpo = "{\"ordem\":1,\"numeroOp\":\"OP-123\",\"codigoProduto\":\"PA-778\",\"alteradoEmCliente\":\"2026-10-21T10:00:00Z\","
                + "\"itens\":[{\"numero\":1,\"valor\":\"C\"},{\"numero\":2,\"valor\":\"NC\",\"observacao\":\"Rótulo sem validade\"},"
                + "{\"numero\":4,\"valor\":\"NP\"},{\"numero\":7,\"valor\":\"P\",\"observacao\":\"Sem luva\"}],"
                + "\"lotes\":[{\"id\":\"" + lote + "\",\"lote\":\"L-55\",\"dataFabricacao\":\"10/2026\"}]}";
        salvarOp(pedro, dia, op, corpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(10)))
                .andExpect(jsonPath("$.itens[1].valor").value("NC"))
                .andExpect(jsonPath("$.itens[6].valor").value("P"))
                .andExpect(jsonPath("$.itens[2].valor").doesNotExist()) // em branco é permitido
                .andExpect(jsonPath("$.lotes[0].lote").value("L-55"));
        // Mesmo envio de novo: continua uma OP só.
        salvarOp(pedro, dia, op, corpo).andExpect(status().isOk());
        mvc.perform(get("/api/inspecoes/" + dia).header("Authorization", pedro)).andExpect(jsonPath("$.ops", hasSize(1)));
        // A fila do aparelho manda em ordem e sempre o estado mais novo: o relógio do aparelho não decide nada.
        // (Um envio com horário "antigo" — relógio atrasado — não pode ser descartado em silêncio.)
        salvarOp(pedro, dia, op, corpo.replace("OP-123", "OP-124").replace("2026-10-21T10:00:00Z", "2026-10-21T09:00:00Z"))
                .andExpect(jsonPath("$.numeroOp").value("OP-124"));
        salvarOp(pedro, dia, op, corpo).andExpect(jsonPath("$.numeroOp").value("OP-123"));
        // Escalas: OM não existe; NC não vale no EPI.
        salvarOp(pedro, dia, op, "{\"ordem\":1,\"numeroOp\":\"OP-123\",\"itens\":[{\"numero\":1,\"valor\":\"OM\"}]}")
                .andExpect(status().isBadRequest());
        salvarOp(pedro, dia, op, "{\"ordem\":1,\"numeroOp\":\"OP-123\",\"itens\":[{\"numero\":7,\"valor\":\"NC\"}]}")
                .andExpect(status().isBadRequest());

        // Foto no tópico 2 (N/C), idempotente pelo id.
        String foto = uuid();
        var arquivo = new MockMultipartFile("arquivo", "rotulo.png", "image/png", png());
        for (int i = 0; i < 2; i++) {
            mvc.perform(multipart("/api/inspecoes/" + dia + "/ops/" + op + "/itens/2/anexos").file(arquivo)
                    .param("anexoId", foto).header("Authorization", pedro).header("X-Dispositivo", DISP))
                    .andExpect(status().isCreated());
        }
        // Arquivo que diz ser imagem mas não é: o PDF não pode quebrar por causa dele.
        mvc.perform(multipart("/api/inspecoes/" + dia + "/ops/" + op + "/itens/1/anexos")
                .file(new MockMultipartFile("arquivo", "quebrada.jpg", "image/jpeg", new byte[] { 1, 2, 3 }))
                .param("anexoId", uuid()).header("Authorization", pedro).header("X-Dispositivo", DISP))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/inspecoes/" + dia).header("Authorization", pedro))
                .andExpect(jsonPath("$.ops[0].itens[1].anexos", hasSize(1)))
                .andExpect(jsonPath("$.ops[0].itens[1].anexos[0].id").value(foto));
        mvc.perform(get("/api/inspecoes/anexos/" + foto).header("Authorization", pedro)).andExpect(status().isOk());

        // Outro inspetor com Preencher vê, mas não altera a inspeção do Pedro.
        mvc.perform(get("/api/inspecoes/" + dia).header("Authorization", lara)).andExpect(status().isOk());
        salvarOp(lara, dia, op, corpo).andExpect(status().isForbidden());

        // Lista com contagens.
        mvc.perform(get("/api/inspecoes").header("Authorization", pedro))
                .andExpect(jsonPath("$[0].totalOps").value(1))
                .andExpect(jsonPath("$[0].naoConformes").value(2))
                .andExpect(jsonPath("$[0].contagem.NC").value(1));

        // PDF para o PCP.
        byte[] pdf = mvc.perform(get("/api/inspecoes/" + dia + "/relatorio.pdf").header("Authorization", pedro))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        try (var doc = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(doc);
            Assertions.assertTrue(texto.contains("OP-123"), texto);
            Assertions.assertTrue(texto.contains("PA-778"), texto);
            Assertions.assertTrue(texto.contains("Rótulo sem validade"), texto);
            Assertions.assertTrue(texto.contains("L-55"), texto);
            Assertions.assertTrue(texto.contains("FQ-106 - Rev. 01 - Data: 12/05/2026"), texto);
            Assertions.assertTrue(texto.contains("Pedro Inspetor"), texto);
        }

        // Finalizar: fica somente leitura; só o admin reabre.
        mvc.perform(post("/api/inspecoes/" + dia + "/finalizar").header("Authorization", pedro).header("X-Dispositivo", DISP))
                .andExpect(jsonPath("$.status").value("FINALIZADA"));
        salvarOp(pedro, dia, op, corpo).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("FINALIZADA"));
        mvc.perform(post("/api/inspecoes/" + dia + "/reabrir").header("Authorization", pedro).header("X-Dispositivo", DISP))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/inspecoes/" + dia + "/reabrir").header("Authorization", admin).header("X-Dispositivo", "pc"))
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));

        // Remover a OP (lógico) e o lote; histórico pelo login.
        salvarOp(pedro, dia, op, "{\"ordem\":1,\"numeroOp\":\"OP-123\",\"codigoProduto\":\"PA-778\",\"lotes\":[]}")
                .andExpect(jsonPath("$.lotes", hasSize(0)));
        mvc.perform(delete("/api/inspecoes/" + dia + "/ops/" + op).header("Authorization", pedro).header("X-Dispositivo", DISP))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/inspecoes/" + dia).header("Authorization", pedro)).andExpect(jsonPath("$.ops", hasSize(0)));
        String hist = mvc.perform(get("/api/inspecoes/" + dia + "/historico").header("Authorization", pedro))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (String ev : List.of("CRIACAO", "OP_INCLUIDA", "OP_ALTERADA", "LOTE_INCLUIDO", "LOTE_REMOVIDO", "ANEXO_INCLUIDO",
                "FINALIZACAO", "REABERTURA", "OP_EXCLUIDA")) {
            Assertions.assertTrue(hist.contains("\"evento\":\"" + ev + "\""), ev + " ausente em " + hist);
        }
        Assertions.assertTrue(hist.contains("\"campo\":\"Identificação e rotulagem\"") && hist.contains("\"usuario\":\"pedro\""), hist);
    }

    @Test
    void opCriadaOfflineGuardaOsTopicosQueOAparelhoMostrou() throws Exception {
        String pedro = login("pedro", "senha-pedro-123");
        String dia = uuid();
        abrirDia(pedro, dia, "2026-10-05").andExpect(status().isOk());
        // O aparelho criou a OP com os tópicos que tinha guardados; o servidor usa esses, não os atuais do formulário.
        salvarOp(pedro, dia, uuid(), "{\"ordem\":1,\"numeroOp\":\"OFF-1\",\"itens\":["
                + "{\"numero\":1,\"topico\":\"Aparência do produto (texto antigo)\",\"valor\":\"C\"},"
                + "{\"numero\":2,\"topico\":\"EPI's\",\"escala\":\"EPI\",\"valor\":\"P\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(2)))
                .andExpect(jsonPath("$.itens[0].topico").value("Aparência do produto (texto antigo)"))
                .andExpect(jsonPath("$.itens[1].escala").value("EPI"))
                .andExpect(jsonPath("$.itens[1].valor").value("P"));
    }

    @Test
    void anexoDeOutroFormularioNaoVazaPeloReenvio() throws Exception {
        String pedro = login("pedro", "senha-pedro-123");
        String dani = login("dani", "senha-dani-123");
        String dia = uuid();
        String op = uuid();
        abrirDia(pedro, dia, "2026-10-06").andExpect(status().isOk());
        salvarOp(pedro, dia, op, "{\"ordem\":1,\"numeroOp\":\"A\"}").andExpect(status().isOk());
        String foto = uuid();
        var arquivo = new MockMultipartFile("arquivo", "x.png", "image/png", png());
        mvc.perform(multipart("/api/inspecoes/" + dia + "/ops/" + op + "/itens/1/anexos").file(arquivo)
                .param("anexoId", foto).header("Authorization", pedro).header("X-Dispositivo", DISP))
                .andExpect(status().isCreated());
        // Dani não tem acesso à inspeção: reenviar o mesmo id não pode devolver os dados do anexo.
        mvc.perform(multipart("/api/inspecoes/" + dia + "/ops/" + op + "/itens/1/anexos").file(arquivo)
                .param("anexoId", foto).header("Authorization", dani).header("X-Dispositivo", "x"))
                .andExpect(status().isForbidden());
        // Excluir uma foto que já não existe (excluída antes de subir) é aceito sem erro.
        mvc.perform(delete("/api/inspecoes/anexos/" + uuid()).header("Authorization", pedro).header("X-Dispositivo", DISP))
                .andExpect(status().isNoContent());
        // Corpo inválido (JSON quebrado) é 400, não 500: um 500 travaria a fila do aparelho.
        salvarOp(pedro, dia, op, "{\"ordem\":").andExpect(status().isBadRequest());
        // Mesmo lote em duas OPs (id repetido) também não pode virar 500.
        String lote = uuid();
        salvarOp(pedro, dia, op, "{\"ordem\":1,\"lotes\":[{\"id\":\"" + lote + "\",\"lote\":\"L1\"}]}").andExpect(status().isOk());
        int st = mvc.perform(put("/api/inspecoes/" + dia + "/ops/" + uuid()).header("Authorization", pedro)
                .header("X-Dispositivo", DISP).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ordem\":2,\"lotes\":[{\"id\":\"" + lote + "\",\"lote\":\"L1\"}]}"))
                .andReturn().getResponse().getStatus();
        Assertions.assertTrue(st >= 400 && st < 500, "esperado 4xx, veio " + st);
    }

    @Test
    void permissoesComFormularioRepetidoNaoQuebram() throws Exception {
        String admin = login("admin", "admin-teste-123");
        Long laraId = usuarios.findByUsernameIgnoreCase("lara").orElseThrow().getId();
        Long fInsp = formInspecao().getId();
        mvc.perform(put("/api/usuarios/" + laraId + "/permissoes").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"templateId\":" + fInsp + ",\"nivel\":\"VER\"},{\"templateId\":" + fInsp + ",\"nivel\":\"PREENCHER\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.templateId == " + fInsp + ")].nivel").value("PREENCHER"));
    }

    @Test
    void adminExcluiInspecaoDeTeste() throws Exception {
        String pedro = login("pedro", "senha-pedro-123");
        String admin = login("admin", "admin-teste-123");
        String dia = uuid();
        abrirDia(pedro, dia, "2026-10-07").andExpect(status().isOk());
        salvarOp(pedro, dia, uuid(), "{\"ordem\":1,\"numeroOp\":\"TESTE\"}").andExpect(status().isOk());
        mvc.perform(post("/api/inspecoes/" + dia + "/finalizar").header("Authorization", pedro).header("X-Dispositivo", DISP))
                .andExpect(status().isOk());

        // Só o admin exclui (mesmo finalizada).
        mvc.perform(delete("/api/inspecoes/" + dia).header("Authorization", pedro).header("X-Dispositivo", DISP))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/inspecoes/" + dia).header("Authorization", admin).header("X-Dispositivo", "pc"))
                .andExpect(status().isNoContent());

        // Some da consulta, da lista e do PDF.
        mvc.perform(get("/api/inspecoes/" + dia).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/inspecoes/" + dia + "/relatorio.pdf").header("Authorization", admin))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/inspecoes").header("Authorization", admin))
                .andExpect(jsonPath("$[?(@.id == '" + dia + "')]").isEmpty());
        // O dia fica livre: o inspetor pode abrir outra inspeção na mesma data.
        abrirDia(pedro, uuid(), "2026-10-07").andExpect(status().isOk());
        // Envio atrasado para a excluída não ressuscita nada.
        salvarOp(pedro, dia, uuid(), "{\"ordem\":2,\"numeroOp\":\"X\"}").andExpect(status().isNotFound());
    }

    /** PNG 2x2 válido. */
    static byte[] png() throws Exception {
        var img = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
