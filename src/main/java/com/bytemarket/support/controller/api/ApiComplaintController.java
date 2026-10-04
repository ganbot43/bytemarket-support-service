package com.bytemarket.support.controller.api;

import com.bytemarket.support.model.Complaint;
import com.bytemarket.support.repository.IComplaintRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Libro de Reclamaciones: registro público y gestión desde el panel.
 */
@RestController
public class ApiComplaintController {

    private static final Set<String> TIPOS_DOC = Set.of("DNI", "CE", "Pasaporte");
    private static final Set<String> TIPOS_BIEN = Set.of("Producto", "Servicio");
    private static final Set<String> TIPOS_RECLAMO = Set.of("Reclamo", "Queja");
    private static final Set<String> ESTADOS = Set.of("pendiente", "en_proceso", "respondido", "cerrado");
    private static final Set<String> ADJUNTOS_PERMITIDOS =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf");
    private static final long MAX_ADJUNTO = 5L * 1024 * 1024;
    private static final Path UPLOAD_DIR = Paths.get("uploads", "complaints").toAbsolutePath();

    @Autowired
    private IComplaintRepository complaintRepository;

    @Autowired
    private com.bytemarket.support.service.EmailService emailService;

    // ─── PÚBLICO ────────────────────────────────────────────────

    @PostMapping(value = "/api/reclamaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> crearMultipart(@RequestParam Map<String, String> form,
                                            @RequestParam(value = "adjunto", required = false) MultipartFile adjunto) {
        return crear(new HashMap<>(form), adjunto);
    }

    @PostMapping(value = "/api/reclamaciones", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> crearJson(@RequestBody Map<String, Object> body) {
        return crear(body, null);
    }

    private ResponseEntity<?> crear(Map<String, ?> data, MultipartFile adjunto) {
        String nombre = txt(data.get("nombre_completo"));
        String tipoDoc = txt(data.get("tipo_documento"));
        String numDoc = txt(data.get("numero_documento"));
        String telefono = txt(data.get("telefono"));
        String email = txt(data.get("email"));
        String tipoBien = txt(data.get("tipo_bien"));
        String descBien = txt(data.get("descripcion_bien"));
        String tipoReclamo = txt(data.get("tipo_reclamo"));
        String descripcion = txt(data.get("descripcion"));
        String pedido = txt(data.get("pedido_cliente"));
        boolean declara = "true".equalsIgnoreCase(String.valueOf(data.get("declara")));

        if (nombre == null || nombre.length() < 2) return error(400, "Ingresa tu nombre completo");
        if (tipoDoc == null || !TIPOS_DOC.contains(tipoDoc)) return error(400, "Tipo de documento inválido");
        if (numDoc == null || numDoc.length() < 3) return error(400, "Número de documento inválido");
        if (telefono == null || telefono.length() < 6) return error(400, "Teléfono inválido");
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) return error(400, "Correo inválido");
        if (tipoBien == null || !TIPOS_BIEN.contains(tipoBien)) return error(400, "Tipo de bien inválido");
        if (descBien == null || descBien.length() < 3) return error(400, "Describe el producto o servicio");
        if (tipoReclamo == null || !TIPOS_RECLAMO.contains(tipoReclamo)) return error(400, "Tipo de reclamo inválido");
        if (descripcion == null || descripcion.length() < 5) return error(400, "Describe tu reclamo");
        if (pedido == null) return error(400, "Indica tu pedido");
        if (!declara) return error(400, "Debe declarar que la información es verídica");

        Double monto = null;
        String rawMonto = txt(data.get("monto_reclamado"));
        if (rawMonto != null) {
            try { monto = Double.valueOf(rawMonto); } catch (NumberFormatException ignored) {}
        }

        String archivoUrl = null;
        if (adjunto != null && !adjunto.isEmpty()) {
            if (adjunto.getSize() > MAX_ADJUNTO) return error(400, "Adjunto demasiado grande (max 5MB)");
            if (adjunto.getContentType() == null || !ADJUNTOS_PERMITIDOS.contains(adjunto.getContentType())) {
                return error(400, "Tipo de archivo no permitido");
            }
            try {
                archivoUrl = guardarAdjunto(adjunto);
            } catch (IOException e) {
                return error(500, "No se pudo guardar el adjunto");
            }
        }

        Complaint c = new Complaint();
        c.setCustomerName(nombre);
        c.setTipoDocumento(tipoDoc);
        c.setNumeroDocumento(numDoc);
        c.setDireccion(txt(data.get("direccion")));
        c.setTelefono(telefono);
        c.setEmail(email);
        c.setTipoBien(tipoBien);
        c.setDescripcionBien(descBien);
        c.setMonto(monto);
        c.setTipoReclamo(tipoReclamo);
        c.setDescripcion(descripcion);
        c.setPedido(pedido);
        c.setArchivoUrl(archivoUrl);
        c.setEstado("pendiente");
        c = complaintRepository.save(c);

        String codigo = "REC-" + Year.now().getValue() + "-" + String.format("%06d", c.getId());
        c.setCodigo(codigo);
        complaintRepository.save(c);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("status", 200);
        res.put("message", "Reclamo registrado correctamente");
        res.put("codigo", codigo);
        res.put("id", c.getId());
        res.put("plazo_respuesta", "15 días hábiles");
        return ResponseEntity.ok(res);
    }

    @GetMapping("/api/reclamaciones/adjuntos/{nombre:.+}")
    public ResponseEntity<Resource> verAdjunto(@PathVariable String nombre) {
        Path file = UPLOAD_DIR.resolve(nombre).normalize();
        if (!file.startsWith(UPLOAD_DIR) || !Files.exists(file)) return ResponseEntity.notFound().build();
        MediaType type = MediaTypeFactory.getMediaType(nombre).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type).body(new FileSystemResource(file));
    }

    // ─── PANEL ADMIN ────────────────────────────────────────────

    @GetMapping("/api/admin/reclamaciones")
    public List<Map<String, Object>> listar() {
        return complaintRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(c -> {
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
    public ResponseEntity<?> detalle(@PathVariable Integer id) {
        return complaintRepository.findById(id).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> error(404, "Reclamo no encontrado"));
    }

    @PutMapping("/api/admin/reclamaciones/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Optional<Complaint> opt = complaintRepository.findById(id);
        if (opt.isEmpty()) return error(404, "Reclamo no encontrado");
        Complaint c = opt.get();

        String estado = txt(body.get("estado"));
        if (estado != null) {
            if (!ESTADOS.contains(estado)) return error(400, "Estado inválido");
            c.setEstado(estado);
        }
        boolean respuestaNueva = false;
        if (body.get("respuesta") instanceof String respuesta && !respuesta.isBlank()) {
            // Solo cuenta como respuesta nueva si cambió: así reabrir y
            // guardar el mismo texto no reenvía el correo al cliente.
            respuestaNueva = !respuesta.equals(c.getRespuesta());
            c.setRespuesta(respuesta);
            c.setFechaRespuesta(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        complaintRepository.save(c);

        // El envío va después de guardar y no corta la respuesta: si el correo
        // falla, el reclamo ya quedó resuelto. Pero se informa el resultado
        // real para que el encargado sepa si debe avisar al cliente por su
        // cuenta, en vez de creer que ya está notificado.
        boolean enviado = respuestaNueva && emailService.sendComplaintResolutionEmail(
                c.getEmail(), c.getCustomerName(), c.getCodigo(), c.getRespuesta());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", 200);
        resp.put("message", "Actualizado");
        resp.put("emailEnviado", enviado);
        if (respuestaNueva && !enviado) {
            resp.put("emailAviso", "El reclamo se guardó, pero no se pudo enviar el correo al cliente.");
        }
        return ResponseEntity.ok(resp);
    }

    // ─── helpers ────────────────────────────────────────────────

    private String guardarAdjunto(MultipartFile file) throws IOException {
        Files.createDirectories(UPLOAD_DIR);
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("adjunto");
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        String nombre = System.currentTimeMillis() + "-" + UUID.randomUUID() + ext.replaceAll("[^a-zA-Z0-9.]", "");
        file.transferTo(UPLOAD_DIR.resolve(nombre).toFile());
        return "/api/reclamaciones/adjuntos/" + nombre;
    }

    private static String txt(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static ResponseEntity<Map<String, Object>> error(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("statusCode", status);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
