package dev.mansano.auditoria.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serve o Angular compilado (classpath:/static). Rotas do Angular (ex.: /auditorias/5),
 * que não são arquivos, devolvem o index.html para o roteador do front resolver.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String caminho, Resource local) throws IOException {
                        Resource r = super.getResource(caminho, local);
                        if (r != null) {
                            return r;
                        }
                        if (caminho.startsWith("api/") || caminho.startsWith("actuator/") || ultimoTemPonto(caminho)) {
                            return null; // 404 de verdade para arquivo inexistente
                        }
                        Resource index = local.createRelative("index.html");
                        return (index.exists() && index.isReadable()) ? index : null;
                    }
                });
    }

    private static boolean ultimoTemPonto(String caminho) {
        return caminho.substring(caminho.lastIndexOf('/') + 1).contains(".");
    }
}
