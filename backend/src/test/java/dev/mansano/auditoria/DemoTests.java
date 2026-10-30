package dev.mansano.auditoria;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import dev.mansano.auditoria.demo.DadosDemo;

/** Perfil "demo": contas de exemplo, dados prontos, contas protegidas e reset. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:demo-teste;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.demo.senha=senha-demo-123",
        "app.armazenamento-dir=./target/anexos-demo-teste"
})
@ActiveProfiles("demo")
@AutoConfigureMockMvc
@DirtiesContext
class DemoTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    DadosDemo dados;

    String login(String user) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"senha\":\"senha-demo-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(json, "$.token");
    }

    @Test
    void contasPublicasEDadosProntos() throws Exception {
        mvc.perform(get("/api/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contas", hasSize(4)))
                .andExpect(jsonPath("$.contas[0].username").value("admin"))
                .andExpect(jsonPath("$.contas[0].senha").value("senha-demo-123"));

        String auditor = login("auditor");
        mvc.perform(get("/api/auditorias").header("Authorization", auditor))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.status == 'CONCLUIDA')]", hasSize(1)));
        mvc.perform(get("/api/templates").header("Authorization", auditor)).andExpect(jsonPath("$", hasSize(5)));

        String inspetor = login("inspetor");
        mvc.perform(get("/api/templates").header("Authorization", inspetor)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/inspecoes").header("Authorization", inspetor))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("FINALIZADA"));
    }

    @Test
    void contasDeExemploNaoMudam() throws Exception {
        String auditor = login("auditor");
        mvc.perform(post("/api/auth/senha").header("Authorization", auditor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"senha-demo-123\",\"novaSenha\":\"outra-senha-123\"}"))
                .andExpect(status().isForbidden());

        String admin = login("admin");
        String eu = mvc.perform(get("/api/auth/eu").header("Authorization", auditor))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(eu, "$.id");
        mvc.perform(put("/api/usuarios/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"X\",\"papel\":\"LEITOR\",\"ativo\":false,\"podeRevisar\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/usuarios/" + id + "/permissoes").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isForbidden());

        // Usuário criado pelo visitante continua editável.
        String novo = mvc.perform(post("/api/usuarios").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"visitante\",\"nome\":\"Visitante\",\"senha\":\"senha-visitante-1\",\"papel\":\"AUDITOR\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer novoId = JsonPath.read(novo, "$.id");
        mvc.perform(put("/api/usuarios/" + novoId).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Visitante 2\",\"papel\":\"LEITOR\",\"ativo\":true,\"podeRevisar\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    void resetVoltaAoEstadoInicial() throws Exception {
        String auditor = login("auditor");
        String templates = mvc.perform(get("/api/templates").header("Authorization", auditor))
                .andReturn().getResponse().getContentAsString();
        Integer tid = JsonPath.read(templates, "$[0].formulario.id");
        mvc.perform(post("/api/auditorias").header("Authorization", auditor).header("X-Dispositivo", "tab")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"VISITA\",\"dataInicio\":\"2026-10-01\",\"auditores\":\"Visitante\",\"templateId\":"
                        + tid + "}"))
                .andExpect(status().isCreated());

        dados.resetar();

        // Sessões antigas caem; a auditoria do visitante sumiu e os dados de exemplo voltaram.
        mvc.perform(get("/api/auditorias").header("Authorization", auditor)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auditorias").header("Authorization", login("auditor")))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.numero == 'VISITA')]").doesNotExist());
    }
}
