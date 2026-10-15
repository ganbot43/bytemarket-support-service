package com.bytemarket.support.service;

import com.bytemarket.support.exception.ComplaintException;
import com.bytemarket.support.model.Complaint;
import com.bytemarket.support.repository.IComplaintRepository;
import com.bytemarket.support.storage.AttachmentStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Las reglas del libro de reclamaciones.
 *
 * Estaban dentro del controlador, mezcladas con el manejo de HTTP, la
 * escritura en disco y el armado de las respuestas. Aquí no se sabe nada de
 * peticiones ni de códigos de estado: se validan datos, se guarda el
 * reclamo y se avisa al cliente.
 *
 * Es la clase que permite probar el libro de reclamaciones sin levantar
 * Spring: sus tres colaboradores entran por el constructor.
 */
@Service
@RequiredArgsConstructor
public class ComplaintService {

    /** Ruta por la que se sirven los adjuntos. La referencia la pone el almacenamiento. */
    public static final String RUTA_ADJUNTOS = "/api/reclamaciones/adjuntos/";

    private static final Set<String> TIPOS_DOC = Set.of("DNI", "CE", "Pasaporte");
    private static final Set<String> TIPOS_BIEN = Set.of("Producto", "Servicio");
    private static final Set<String> TIPOS_RECLAMO = Set.of("Reclamo", "Queja");
    private static final Set<String> ESTADOS = Set.of("pendiente", "en_proceso", "respondido", "cerrado");
    private static final Set<String> ADJUNTOS_PERMITIDOS =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf");
    private static final long MAX_ADJUNTO = 5L * 1024 * 1024;
    private static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private final IComplaintRepository complaintRepository;
    private final EmailService emailService;
    private final AttachmentStorage attachmentStorage;

    // ─── Registro público ───────────────────────────────────────

    /**
     * Valida, guarda y numera un reclamo nuevo.
     *
     * El código sale del id, así que hace falta guardar antes de poder
     * numerarlo: de ahí los dos save.
     */
    @Transactional
    public Complaint registrar(Map<String, ?> datos, MultipartFile adjunto) {
        Complaint c = new Complaint();

        c.setCustomerName(exigir(datos, "nombre_completo", 2, "Ingresa tu nombre completo"));
        c.setTipoDocumento(exigirDe(datos, "tipo_documento", TIPOS_DOC, "Tipo de documento inválido"));
        c.setNumeroDocumento(exigir(datos, "numero_documento", 3, "Número de documento inválido"));
        c.setTelefono(exigir(datos, "telefono", 6, "Teléfono inválido"));

        String email = texto(datos.get("email"));
        if (email == null || !email.matches(EMAIL)) throw ComplaintException.invalida("Correo inválido");
        c.setEmail(email);

        c.setTipoBien(exigirDe(datos, "tipo_bien", TIPOS_BIEN, "Tipo de bien inválido"));
        c.setDescripcionBien(exigir(datos, "descripcion_bien", 3, "Describe el producto o servicio"));
        c.setTipoReclamo(exigirDe(datos, "tipo_reclamo", TIPOS_RECLAMO, "Tipo de reclamo inválido"));
        c.setDescripcion(exigir(datos, "descripcion", 5, "Describe tu reclamo"));
        c.setPedido(exigir(datos, "pedido_cliente", 1, "Indica tu pedido"));

        if (!"true".equalsIgnoreCase(String.valueOf(datos.get("declara")))) {
            throw ComplaintException.invalida("Debe declarar que la información es verídica");
        }

        c.setDireccion(texto(datos.get("direccion")));
        c.setMonto(monto(datos.get("monto_reclamado")));
        c.setArchivoUrl(guardarAdjunto(adjunto));
        c.setEstado("pendiente");

        c = complaintRepository.save(c);
        c.setCodigo("REC-" + Year.now().getValue() + "-" + String.format("%06d", c.getId()));
        return complaintRepository.save(c);
    }

    /**
     * Comprueba el adjunto y lo manda a guardar. Devuelve la URL con la que
     * se servirá, o null si no venía ninguno.
     */
    private String guardarAdjunto(MultipartFile adjunto) {
        if (adjunto == null || adjunto.isEmpty()) return null;

        if (adjunto.getSize() > MAX_ADJUNTO) {
            throw ComplaintException.invalida("Adjunto demasiado grande (max 5MB)");
        }
        if (adjunto.getContentType() == null || !ADJUNTOS_PERMITIDOS.contains(adjunto.getContentType())) {
            throw ComplaintException.invalida("Tipo de archivo no permitido");
        }
        try {
            return RUTA_ADJUNTOS + attachmentStorage.guardar(adjunto);
        } catch (IOException e) {
            throw new ComplaintException(500, "No se pudo guardar el adjunto");
        }
    }

    // ─── Panel ──────────────────────────────────────────────────

    public List<Complaint> listar() {
        return complaintRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Complaint buscar(Integer id) {
        return complaintRepository.findById(id).orElseThrow(ComplaintException::noEncontrada);
    }

    /**
     * Resultado de atender un reclamo: si se escribió una respuesta nueva y
     * si el correo al cliente salió. Se devuelven las dos porque el panel
     * tiene que poder decirle al encargado "guardado, pero avisa tú".
     */
    public record Atencion(boolean respuestaNueva, boolean emailEnviado) {}

    /**
     * Cambia el estado y/o escribe la respuesta.
     *
     * El correo se manda después de guardar y su fallo no revierte nada: si
     * el envío falla, el reclamo ya quedó atendido. Pero se informa el
     * resultado real, para que nadie dé por avisado a un cliente que no lo
     * está.
     */
    @Transactional
    public Atencion atender(Integer id, Map<String, Object> cambios) {
        Complaint c = buscar(id);

        String estado = texto(cambios.get("estado"));
        if (estado != null) {
            if (!ESTADOS.contains(estado)) throw ComplaintException.invalida("Estado inválido");
            c.setEstado(estado);
        }

        boolean respuestaNueva = false;
        if (cambios.get("respuesta") instanceof String respuesta && !respuesta.isBlank()) {
            // Solo cuenta como nueva si cambió: reabrir y guardar el mismo
            // texto no debe reenviar el correo al cliente.
            respuestaNueva = !respuesta.equals(c.getRespuesta());
            c.setRespuesta(respuesta);
            c.setFechaRespuesta(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        complaintRepository.save(c);

        boolean enviado = respuestaNueva && emailService.sendComplaintResolutionEmail(
                c.getEmail(), c.getCustomerName(), c.getCodigo(), c.getRespuesta());

        return new Atencion(respuestaNueva, enviado);
    }

    /** Los bytes de un adjunto, si la referencia existe. */
    public Optional<org.springframework.core.io.Resource> adjunto(String referencia) {
        return attachmentStorage.abrir(referencia);
    }

    // ─── Validación ─────────────────────────────────────────────

    private static String exigir(Map<String, ?> datos, String campo, int minimo, String mensaje) {
        String v = texto(datos.get(campo));
        if (v == null || v.length() < minimo) throw ComplaintException.invalida(mensaje);
        return v;
    }

    private static String exigirDe(Map<String, ?> datos, String campo, Set<String> validos, String mensaje) {
        String v = texto(datos.get(campo));
        if (v == null || !validos.contains(v)) throw ComplaintException.invalida(mensaje);
        return v;
    }

    private static Double monto(Object raw) {
        String s = texto(raw);
        if (s == null) return null;
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            // Un monto ilegible no invalida el reclamo: es opcional.
            return null;
        }
    }

    private static String texto(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
