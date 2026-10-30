package dev.mansano.auditoria.comum;

import java.time.Instant;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record Erro(String codigo, String mensagem, Instant momento) {
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<Erro> negocio(NegocioException e) {
        return ResponseEntity.status(e.getStatus()).body(new Erro(e.getCodigo(), e.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Erro> validacao(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new Erro("INVALIDO", msg, Instant.now()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Erro> tamanho(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new Erro("ARQUIVO_GRANDE", "Arquivo maior que o limite permitido.", Instant.now()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Erro> acesso(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new Erro("PROIBIDO", "Você não tem permissão para esta ação.", Instant.now()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Erro> semRecurso(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new Erro("NAO_ENCONTRADO", "Recurso não encontrado.", Instant.now()));
    }

    /** Corpo ilegível (JSON quebrado, tipo errado): 400. Um 500 aqui travaria a fila offline do aparelho. */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Erro> corpoInvalido(Exception e) {
        return ResponseEntity.badRequest().body(new Erro("INVALIDO", "Dados enviados em formato inválido.", Instant.now()));
    }

    /** Chave repetida ou referência quebrada: 409, para o aparelho marcar como recusada em vez de esperar para sempre. */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Erro> integridade(Exception e) {
        log.warn("Violação de integridade: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Erro("DADOS_CONFLITANTES", "Os dados enviados conflitam com os já gravados.", Instant.now()));
    }

    /** Duas gravações simultâneas no mesmo registro: 409 CONFLITO; o aparelho tenta de novo depois. */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Erro> concorrencia(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Erro("CONFLITO", "Outra gravação aconteceu ao mesmo tempo. Tente de novo.", Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Erro> inesperado(Exception e) {
        log.error("Erro inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new Erro("ERRO_INTERNO", "Erro interno no servidor.", Instant.now()));
    }
}
