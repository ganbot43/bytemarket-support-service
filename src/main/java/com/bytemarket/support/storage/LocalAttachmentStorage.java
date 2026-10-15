package com.bytemarket.support.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

/**
 * Adjuntos en el disco del propio servicio.
 *
 * El directorio es configurable a propósito: dentro de Docker tiene que
 * caer en un volumen, porque si queda en la capa de escritura del
 * contenedor los adjuntos desaparecen en cuanto se actualiza la imagen.
 */
@Component
public class LocalAttachmentStorage implements AttachmentStorage {

    private final Path raiz;

    public LocalAttachmentStorage(@Value("${attachments.dir:uploads/complaints}") String dir) {
        this.raiz = Paths.get(dir).toAbsolutePath().normalize();
    }

    @Override
    public String guardar(MultipartFile archivo) throws IOException {
        Files.createDirectories(raiz);

        String original = Optional.ofNullable(archivo.getOriginalFilename()).orElse("adjunto");
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        // El nombre lo pone el servidor: el del cliente puede traer rutas.
        String referencia = System.currentTimeMillis() + "-" + UUID.randomUUID()
                + ext.replaceAll("[^a-zA-Z0-9.]", "");

        archivo.transferTo(raiz.resolve(referencia).toFile());
        return referencia;
    }

    @Override
    public Optional<Resource> abrir(String referencia) {
        if (referencia == null || referencia.isBlank()) return Optional.empty();

        Path archivo = raiz.resolve(referencia).normalize();
        // Una referencia con ".." se saldría del directorio: se rechaza.
        if (!archivo.startsWith(raiz) || !Files.exists(archivo)) return Optional.empty();

        return Optional.of(new FileSystemResource(archivo));
    }
}
