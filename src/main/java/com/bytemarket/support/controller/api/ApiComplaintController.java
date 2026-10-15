package com.bytemarket.support.controller.api;

import com.bytemarket.support.model.Complaint;
import com.bytemarket.support.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Libro de Reclamaciones: registro público y gestión desde el panel.
 *
 * Aquí solo queda lo que es HTTP: adaptar la petición (que llega como
 * formulario o como JSON), elegir el código de estado y dar forma a la
 * respuesta. Las reglas están en ComplaintService y el almacenamiento del
 * adjunto detrás de AttachmentStorage.
 */
@RestController
@RequiredArgsConstructor
public class ApiComplaintController {

    private final ComplaintService complaintService;

    // ─── PÚBLICO ────────────────────────────────────────────────

    /**
     * El formulario de la web manda multipart porque puede traer adjunto;
     * otros clientes mandan JSON. Las dos entradas se normalizan a un mapa
     * y de ahí en adelante el servicio no distingue.
     */
    @PostMapping(value = "/api/reclamaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> crearMultipart(@RequestParam Map<String, String> form,
                                            @RequestParam(value = "adjunto", required = false) MultipartFile adjunto) {
        return creado(complaintService.registrar(new HashMap<>(form), adjunto));
    }

    @PostMapping(value = "/api/reclamaciones", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> crearJson(@RequestBody Map<String, Object> body) {
        return creado(complaintService.registrar(body, null));
    }

    private ResponseEntity<?> creado(Complaint c) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("status", 200);
        res.put("message", "Reclamo registrado correctamente");
        res.put("codigo", c.getCodigo());
        res.put("id", c.getId());
        res.put("plazo_respuesta", "15 días hábiles");
        return ResponseEntity.ok(res);
    }

    @GetMapping("/api/reclamaciones/adjuntos/{referencia:.+}")
    public ResponseEntity<Resource> verAdjunto(@PathVariable String referencia) {
        return complaintService.adjunto(referencia)
                .map(archivo -> ResponseEntity.ok()
                        .contentType(MediaTypeFactory.getMediaType(referencia)
                                .orElse(MediaType.APPLICATION_OCTET_STREAM))
                        .body(archivo))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ─── PANEL ADMIN ────────────────────────────────────────────

    /** La tabla del panel solo necesita seis campos de cada reclamo. */
    @GetMapping("/api/admin/reclamaciones")
    public List<Map<String, Object>> listar() {
        return complaintService.listar().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("codigo", c.getCodigo());
            m.put("nombre", c.getCustomerName());
            m.put("tipo", c.getTipoReclamo());
            m.put("estado", c.getEstado());
            m.put("createdAt", c.getCreatedAt());
            return m;
        }).toList();
    }

    @GetMapping("/api/admin/reclamaciones/{id}")
    public Complaint detalle(@PathVariable Integer id) {
        return complaintService.buscar(id);
    }

    @PutMapping("/api/admin/reclamaciones/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        ComplaintService.Atencion atencion = complaintService.atender(id, body);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", 200);
        resp.put("message", "Actualizado");
        resp.put("emailEnviado", atencion.emailEnviado());
        if (atencion.respuestaNueva() && !atencion.emailEnviado()) {
            resp.put("emailAviso", "El reclamo se guardó, pero no se pudo enviar el correo al cliente.");
        }
        return ResponseEntity.ok(resp);
    }
}
