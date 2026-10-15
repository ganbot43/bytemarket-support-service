package com.bytemarket.support.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guardado en disco de los adjuntos del libro de reclamaciones.
 *
 * Misma convención que las subidas a S3: aaaa/MM/dd y el nombre lo pone el
 * servidor.
 */
class LocalAttachmentStorageTest {

    @TempDir
    Path dir;

    private static String hoy() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
    }

    @Test
    @DisplayName("la referencia lleva aaaa/MM/dd y el archivo se recupera por ella")
    void guardaPorFechaYRecupera() throws IOException {
        LocalAttachmentStorage storage = new LocalAttachmentStorage(dir.toString());
        MockMultipartFile png = new MockMultipartFile("adjunto", "boleta.PNG", "image/png",
                "contenido".getBytes());

        String referencia = storage.guardar(png);

        assertTrue(referencia.startsWith(hoy() + "/"), "esperaba " + hoy() + "/..., salió " + referencia);
        assertTrue(referencia.endsWith(".png"), "la extensión se guarda en minúscula: " + referencia);
        assertTrue(Files.exists(dir.resolve(referencia)));
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

        assertFalse(referencia.contains(".."), referencia);
        // Las únicas barras son las tres de la fecha.
        assertEquals(4, referencia.split("/").length, referencia);
        assertTrue(Files.exists(dir.resolve(referencia)));
    }

    @Test
    @DisplayName("los adjuntos antiguos, guardados en plano, se siguen abriendo")
    void referenciaAntiguaSigueValiendo() throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("1733-abcd.png"), "viejo");

        LocalAttachmentStorage storage = new LocalAttachmentStorage(dir.toString());

        assertEquals("viejo",
                new String(storage.abrir("1733-abcd.png").orElseThrow().getInputStream().readAllBytes()),
                "las URLs ya guardadas en base apuntan a referencias sin fecha");
    }

    @Test
    @DisplayName("una referencia que se sale del directorio no se abre")
    void noSaleDelDirectorio() {
        LocalAttachmentStorage storage = new LocalAttachmentStorage(dir.resolve("adjuntos").toString());

        assertTrue(storage.abrir("../../../etc/passwd").isEmpty());
        assertTrue(storage.abrir("2026/10/15/no-existe.png").isEmpty());
        assertTrue(storage.abrir(null).isEmpty());
    }
}
