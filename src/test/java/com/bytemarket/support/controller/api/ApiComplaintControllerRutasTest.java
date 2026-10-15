package com.bytemarket.support.controller.api;

import com.bytemarket.support.service.ComplaintService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * La ruta con la que se sirven los adjuntos.
 *
 * Desde que la referencia lleva fecha —2026/10/15/archivo.png— la ruta tiene
 * que capturar el resto del camino y no un solo segmento. Es comportamiento
 * de Spring, no del servicio, así que se comprueba levantando el controlador.
 */
@WebMvcTest(controllers = ApiComplaintController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApiComplaintControllerRutasTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ComplaintService complaintService;

    @Test
    @DisplayName("una referencia con fecha llega entera al servicio, sin la barra inicial")
    void referenciaConFecha() throws Exception {
        when(complaintService.adjunto(anyString()))
                .thenReturn(Optional.of(new ByteArrayResource("bytes".getBytes())));

        mockMvc.perform(get("/api/reclamaciones/adjuntos/2026/10/15/abc-123.png"))
                .andExpect(status().isOk());

        verify(complaintService).adjunto(eq("2026/10/15/abc-123.png"));
    }

    @Test
    @DisplayName("una referencia antigua, sin fecha, sigue resolviendo")
    void referenciaAntigua() throws Exception {
        when(complaintService.adjunto(anyString()))
                .thenReturn(Optional.of(new ByteArrayResource("bytes".getBytes())));

        mockMvc.perform(get("/api/reclamaciones/adjuntos/1733-abcd.png"))
                .andExpect(status().isOk());

        verify(complaintService).adjunto(eq("1733-abcd.png"));
    }

    @Test
    @DisplayName("si el adjunto no está, 404")
    void adjuntoInexistente() throws Exception {
        when(complaintService.adjunto(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/reclamaciones/adjuntos/2026/10/15/no-existe.png"))
                .andExpect(status().isNotFound());
    }
}
