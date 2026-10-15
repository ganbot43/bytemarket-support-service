package com.bytemarket.support.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Guardado en disco: nombre seguro, recuperación y salida del directorio. */
class LocalAttachmentStorageTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("guarda el archivo y lo devuelve por su referencia")
    void guardaYRecupera() throws IOException {
        LocalAttachmentStorage storage = new LocalAttachmentStorage(dir.toString());
        MockMultipartFile png = new MockMultipartFile("adjunto", "boleta.png", "image/png",
                "contenido".getBytes());

        String referencia = storage.guardar(png);

        assertTrue(Files.exists(dir.resolve(referencia)));
        assertTrue(storage.abrir(referencia).isPresent());
        assertEquals("contenido",
                new String(storage.abrir(referencia).orElseThrow().getInputStream().readAllBytes()));
    }

    @Test
    @DisplayName("el nombre lo pone el servidor: el del cliente puede traer ruta")
    void ignoraElNombreDelCliente() throws IOException {
        LocalAttachmentStorage storage = new LocalAttachmentStorage(dir.toString());
        MockMultipartFile malicioso = new MockMultipartFile("adjunto", "../../etc/passwd",
                "application/pdf", new byte[]{1});

        String referencia = storage.guardar(malicioso);

        assertFalse(referencia.contains("/"), "la referencia no debe ser una ruta");
        assertFalse(referencia.contains(".."));
        assertTrue(Files.exists(dir.resolve(referencia)));
    }

    @Test
    @DisplayName("una referencia que se sale del directorio no se abre")
    void noSaleDelDirectorio() {
        LocalAttachmentStorage storage = new LocalAttachmentStorage(dir.resolve("adjuntos").toString());

        assertTrue(storage.abrir("../../../etc/passwd").isEmpty());
        assertTrue(storage.abrir("no-existe.png").isEmpty());
        assertTrue(storage.abrir(null).isEmpty());
    }
}
