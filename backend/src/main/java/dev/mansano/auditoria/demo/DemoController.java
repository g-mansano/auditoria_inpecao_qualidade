package dev.mansano.auditoria.demo;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mansano.auditoria.comum.NegocioException;
import dev.mansano.auditoria.config.AppProperties;

/** Público: a tela de login mostra as contas de exemplo quando o servidor está em modo demonstração. */
@RestController
public class DemoController {

    private final AppProperties props;

    public DemoController(AppProperties props) {
        this.props = props;
    }

    public record ContaDto(String username, String senha, String nome, String papel, String descricao) {
    }

    public record DemoDto(List<ContaDto> contas) {
    }

    @GetMapping("/api/demo")
    public DemoDto demo() {
        if (!props.demo().ativo()) {
            throw NegocioException.naoEncontrado("Modo demonstração");
        }
        return new DemoDto(ContasDemo.TODAS.stream()
                .map(c -> new ContaDto(c.username(), props.demo().senha(), c.nome(), c.papel().name(), c.descricao()))
                .toList());
    }
}
