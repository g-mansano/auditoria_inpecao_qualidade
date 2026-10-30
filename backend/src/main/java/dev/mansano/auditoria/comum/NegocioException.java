package dev.mansano.auditoria.comum;

import org.springframework.http.HttpStatus;

/** Erro de regra de negócio, devolvido ao cliente com status HTTP e mensagem legível. */
public class NegocioException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public NegocioException(HttpStatus status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }

    public static NegocioException naoEncontrado(String oque) {
        return new NegocioException(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", oque + " não encontrado(a).");
    }

    public static NegocioException invalido(String mensagem) {
        return new NegocioException(HttpStatus.BAD_REQUEST, "INVALIDO", mensagem);
    }

    public static NegocioException proibido(String mensagem) {
        return new NegocioException(HttpStatus.FORBIDDEN, "PROIBIDO", mensagem);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }
}
