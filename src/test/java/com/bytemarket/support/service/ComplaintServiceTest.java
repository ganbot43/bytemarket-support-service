package com.bytemarket.support.service;

import com.bytemarket.support.exception.ComplaintException;
import com.bytemarket.support.model.Complaint;
import com.bytemarket.support.repository.IComplaintRepository;
import com.bytemarket.support.storage.AttachmentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Reglas del libro de reclamaciones.
 *
 * Estos tests no existían porque la lógica vivía en el controlador con sus
 * dependencias inyectadas por campo: no había forma de construirlo con
 * mocks. Con el servicio aparte, basta pasarle los tres colaboradores.
 */
class ComplaintServiceTest {

    private IComplaintRepository complaintRepository;
    private EmailService emailService;
    private AttachmentStorage attachmentStorage;
    private ComplaintService service;

    @BeforeEach
    void setUp() {
        complaintRepository = mock(IComplaintRepository.class);
        emailService = mock(EmailService.class);
        attachmentStorage = mock(AttachmentStorage.class);
        service = new ComplaintService(complaintRepository, emailService, attachmentStorage);

        // El id lo pone la base; aquí se simula en el primer save porque el
        // código del reclamo se deriva de él.
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(i -> {
            Complaint c = i.getArgument(0);
            if (c.getId() == null) c.setId(42);
            return c;
        });
    }

    private Map<String, Object> formularioValido() {
        Map<String, Object> f = new HashMap<>();
        f.put("nombre_completo", "Gino Barrena");
        f.put("tipo_documento", "DNI");
        f.put("numero_documento", "70123456");
        f.put("telefono", "923821520");
        f.put("email", "cliente@bytemarket.com");
        f.put("tipo_bien", "Producto");
        f.put("descripcion_bien", "Pantalla iPhone 11");
        f.put("tipo_reclamo", "Reclamo");
        f.put("descripcion", "Llegó con un píxel muerto");
        f.put("pedido_cliente", "BM-20261004-1234");
        f.put("declara", "true");
        return f;
    }

    // ─── Registro ───────────────────────────────────────────────

    @Test
    @DisplayName("un reclamo válido se guarda y se numera a partir del id")
    void registraYNumera() {
        Complaint c = service.registrar(formularioValido(), null);

        assertEquals("REC-" + java.time.Year.now().getValue() + "-000042", c.getCodigo());
        assertEquals("pendiente", c.getEstado());
        assertNull(c.getArchivoUrl());
        // Dos save: uno para obtener el id, otro para dejar el código.
        verify(complaintRepository, times(2)).save(any(Complaint.class));
    }

    @Test
    @DisplayName("sin declaración de veracidad no se registra")
    void exigeDeclaracion() {
        Map<String, Object> f = formularioValido();
        f.put("declara", "false");

        ComplaintException e = assertThrows(ComplaintException.class, () -> service.registrar(f, null));
        assertEquals(400, e.getStatus());
        verifyNoInteractions(complaintRepository);
    }

    @Test
    @DisplayName("un tipo de documento que no está en la lista se rechaza")
    void validaTipoDocumento() {
        Map<String, Object> f = formularioValido();
        f.put("tipo_documento", "RUC");

        assertEquals("Tipo de documento inválido",
                assertThrows(ComplaintException.class, () -> service.registrar(f, null)).getMessage());
    }

    @Test
    @DisplayName("un correo sin forma de correo se rechaza")
    void validaCorreo() {
        Map<String, Object> f = formularioValido();
        f.put("email", "cliente-arroba-bytemarket");

        assertEquals("Correo inválido",
                assertThrows(ComplaintException.class, () -> service.registrar(f, null)).getMessage());
    }

    @Test
    @DisplayName("un monto ilegible no tumba el reclamo: es opcional")
    void montoIlegibleNoInvalida() {
        Map<String, Object> f = formularioValido();
        f.put("monto_reclamado", "ciento veinte");

        assertNull(service.registrar(f, null).getMonto());
    }

    // ─── Adjunto ────────────────────────────────────────────────

    @Test
    @DisplayName("el adjunto se guarda y queda su URL en el reclamo")
    void guardaAdjunto() throws IOException {
        when(attachmentStorage.guardar(any())).thenReturn("1234-abcd.png");
        MockMultipartFile png = new MockMultipartFile("adjunto", "boleta.png", "image/png", new byte[]{1, 2, 3});

        Complaint c = service.registrar(formularioValido(), png);

        assertEquals(ComplaintService.RUTA_ADJUNTOS + "1234-abcd.png", c.getArchivoUrl());
    }

    @Test
    @DisplayName("un tipo de archivo no permitido se rechaza sin tocar el almacenamiento")
    void rechazaTipoNoPermitido() {
        MockMultipartFile exe = new MockMultipartFile("adjunto", "virus.exe",
                "application/x-msdownload", new byte[]{1});

        assertEquals("Tipo de archivo no permitido",
                assertThrows(ComplaintException.class,
                        () -> service.registrar(formularioValido(), exe)).getMessage());
        verifyNoInteractions(attachmentStorage);
    }

    @Test
    @DisplayName("si el almacenamiento falla, es un 500 y no un 400")
    void fallaAlGuardarAdjunto() throws IOException {
        when(attachmentStorage.guardar(any())).thenThrow(new IOException("disco lleno"));
        MockMultipartFile png = new MockMultipartFile("adjunto", "boleta.png", "image/png", new byte[]{1});

        assertEquals(500, assertThrows(ComplaintException.class,
                () -> service.registrar(formularioValido(), png)).getStatus());
    }

    // ─── Atención desde el panel ────────────────────────────────

    @Test
    @DisplayName("una respuesta nueva manda el correo al cliente")
    void respuestaNuevaAvisa() {
        Complaint c = guardado();
        when(complaintRepository.findById(42)).thenReturn(Optional.of(c));
        when(emailService.sendComplaintResolutionEmail(any(), any(), any(), any())).thenReturn(true);

        ComplaintService.Atencion a = service.atender(42, Map.of("respuesta", "Te reponemos la pantalla"));

        assertTrue(a.respuestaNueva());
        assertTrue(a.emailEnviado());
        verify(emailService).sendComplaintResolutionEmail(
                "cliente@bytemarket.com", "Gino Barrena", "REC-2026-000042", "Te reponemos la pantalla");
    }

    @Test
    @DisplayName("guardar la misma respuesta otra vez no reenvía el correo")
    void mismaRespuestaNoReenvia() {
        Complaint c = guardado();
        c.setRespuesta("Te reponemos la pantalla");
        when(complaintRepository.findById(42)).thenReturn(Optional.of(c));

        ComplaintService.Atencion a = service.atender(42, Map.of("respuesta", "Te reponemos la pantalla"));

        assertFalse(a.respuestaNueva());
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("si el correo falla, el reclamo queda guardado igual y se informa")
    void correoFallidoNoRevierte() {
        when(complaintRepository.findById(42)).thenReturn(Optional.of(guardado()));
        when(emailService.sendComplaintResolutionEmail(any(), any(), any(), any())).thenReturn(false);

        ComplaintService.Atencion a = service.atender(42, Map.of("respuesta", "Te reponemos la pantalla"));

        assertTrue(a.respuestaNueva());
        assertFalse(a.emailEnviado(), "el panel tiene que poder decir 'avisa tú'");
        verify(complaintRepository).save(any(Complaint.class));
    }

    @Test
    @DisplayName("un estado que no existe se rechaza")
    void validaEstado() {
        when(complaintRepository.findById(42)).thenReturn(Optional.of(guardado()));

        assertEquals("Estado inválido",
                assertThrows(ComplaintException.class,
                        () -> service.atender(42, Map.of("estado", "archivado"))).getMessage());
    }

    @Test
    @DisplayName("atender un reclamo que no existe es 404")
    void noEncontrado() {
        when(complaintRepository.findById(99)).thenReturn(Optional.empty());

        assertEquals(404, assertThrows(ComplaintException.class,
                () -> service.atender(99, Map.of("estado", "cerrado"))).getStatus());
    }

    private Complaint guardado() {
        Complaint c = new Complaint();
        c.setId(42);
        c.setCodigo("REC-2026-000042");
        c.setCustomerName("Gino Barrena");
        c.setEmail("cliente@bytemarket.com");
        c.setEstado("pendiente");
        return c;
    }
}
