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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Adjuntos en el disco del propio servicio.
 *
 * La referencia es {aaaa}/{MM}/{dd}/{uuid}.{ext}, la misma convención con la
 * que catalog y user guardan en S3. Aquí sirve para lo mismo: poder barrer
 * por antigüedad y encontrar lo de un día concreto sin listar la carpeta
 * entera.
 *
 * El directorio raíz es configurable a propósito: dentro de Docker tiene que
 * caer en un volumen, porque si queda en la capa de escritura del contenedor
 * los adjuntos desaparecen en cuanto se actualiza la imagen.
 */
@Component
public class LocalAttachmentStorage implements AttachmentStorage {

    private final Path raiz;

    public LocalAttachmentStorage(@Value("${attachments.dir:uploads/complaints}") String dir) {
        this.raiz = Paths.get(dir).toAbsolutePath().normalize();
    }

    @Override
    public String guardar(MultipartFile archivo) throws IOException {
        String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        // El nombre lo pone el servidor: del original solo la extensión, que
        // el nombre del cliente puede traer rutas, espacios o acentos.
        String referencia = fecha + "/" + UUID.randomUUID() + extension(archivo.getOriginalFilename());

        Path destino = raiz.resolve(referencia);
        Files.createDirectories(destino.getParent());
        archivo.transferTo(destino.toFile());

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

    /** La extensión, si es una extensión de verdad y no un nombre disfrazado. */
    private static String extension(String originalFilename) {
        if (originalFilename == null) return "";

        String base = originalFilename.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);

        int punto = base.lastIndexOf('.');
        if (punto < 0) return "";

        String ext = base.substring(punto).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,8}") ? ext : "";
    }
}
