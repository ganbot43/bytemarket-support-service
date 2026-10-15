package com.bytemarket.support.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

/**
 * Dónde se guardan los adjuntos del libro de reclamaciones.
 *
 * Existe porque el controlador hablaba con el disco directamente
 * —Paths.get("uploads","complaints")— mientras el resto del proyecto usa S3
 * a través de un servicio. Con esta interfaz, quien registra un reclamo no
 * sabe ni le importa si el archivo acaba en disco, en S3 o en memoria
 * durante un test.
 *
 * Son dos operaciones y nada más: guardar bytes y devolverlos. Construir la
 * URL pública o decidir qué tipos se aceptan son decisiones de quien recibe
 * la petición, no de quien guarda.
 */
public interface AttachmentStorage {

    /**
     * Guarda el archivo y devuelve la referencia con la que recuperarlo.
     * La referencia es opaca: no hay que suponer que es una ruta.
     */
    String guardar(MultipartFile archivo) throws IOException;

    /** Los bytes, o vacío si esa referencia no existe. */
    Optional<Resource> abrir(String referencia);
}
