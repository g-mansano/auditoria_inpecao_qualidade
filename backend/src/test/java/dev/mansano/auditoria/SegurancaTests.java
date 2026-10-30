package dev.mansano.auditoria;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import dev.mansano.auditoria.seguranca.LoginRateLimiter;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seguranca;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.admin-inicial.senha=senha-admin-segura-123"
})
@AutoConfigureMockMvc
@DirtiesContext
class SegurancaTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    LoginRateLimiter rateLimiter;

    @Test
    void classpathTraversalBloqueado() throws Exception {
        // Tentativa de acessar application.properties via traversal estático é bloqueada (400 ou 404) sem vazar o arquivo
        mvc.perform(get("/../application.properties"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void bruteForceLoginBloqueado() throws Exception {
        String user = "usuario-inexistente-" + System.currentTimeMillis();

        // 5 tentativas erradas permitidas (retornam 401)
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + user + "\",\"senha\":\"senha-errada\"}"))
                    .andExpect(status().isUnauthorized());
        }

        // A 6ª tentativa deve ser rejeitada com 429 Too Many Requests
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"senha\":\"senha-errada\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void trocaDeSenhaRevogaSessoesAtivas() throws Exception {
        // Login inicial
        String respLogin = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"senha\":\"senha-admin-segura-123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = "Bearer " + JsonPath.read(respLogin, "$.token");

        // Token funciona
        mvc.perform(get("/api/auth/eu").header("Authorization", token))
                .andExpect(status().isOk());

        // Troca de senha
        mvc.perform(post("/api/auth/senha")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"senha-admin-segura-123\",\"novaSenha\":\"nova-senha-admin-999\"}"))
                .andExpect(status().isNoContent());

        // Token antigo foi revogado e agora dá 401
        mvc.perform(get("/api/auth/eu").header("Authorization", token))
                .andExpect(status().isUnauthorized());

        // Login com senha nova funciona
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"senha\":\"nova-senha-admin-999\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void idInvalidoEmInspecaoRecusado() throws Exception {
        String respLogin = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"senha\":\"senha-admin-segura-123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = "Bearer " + JsonPath.read(respLogin, "$.token");

        // ID com caracteres de traversal ou injection é rejeitado
        mvc.perform(get("/api/inspecoes/../malicioso")
                .header("Authorization", token))
                .andExpect(status().isBadRequest());
    }
}
