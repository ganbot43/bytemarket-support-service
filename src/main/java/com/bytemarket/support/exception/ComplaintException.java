package com.bytemarket.support.exception;

/**
 * Una regla del libro de reclamaciones que no se cumple.
 *
 * Lleva el estado HTTP porque las reglas no fallan todas igual: un campo
 * mal puesto es 400, un reclamo que no existe es 404 y un adjunto que no se
 * pudo escribir es 500. Antes esa distinción vivía en los `return error(…)`
 * repartidos por el controlador.
 */
public class ComplaintException extends RuntimeException {

    private final int status;

    public ComplaintException(int status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public static ComplaintException invalida(String mensaje) {
        return new ComplaintException(400, mensaje);
    }

    public static ComplaintException noEncontrada() {
        return new ComplaintException(404, "Reclamo no encontrado");
    }

    public int getStatus() {
        return status;
    }
}
