package com.bytemarket.support.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "complaints")
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 100, nullable = false)
    private String codigo = "";

    @Column(name = "customer_name", nullable = false, columnDefinition = "TEXT")
    private String customerName;

    @Column(name = "tipo_documento", nullable = false, columnDefinition = "TEXT")
    private String tipoDocumento;

    @Column(name = "numero_documento", nullable = false, columnDefinition = "TEXT")
    private String numeroDocumento;

    @Column(columnDefinition = "TEXT")
    private String direccion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String telefono;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String email;

    @Column(name = "tipo_bien", nullable = false, columnDefinition = "TEXT")
    private String tipoBien;

    @Column(name = "descripcion_bien", nullable = false, columnDefinition = "TEXT")
    private String descripcionBien;

    private Double monto;

    @Column(name = "tipo_reclamo", nullable = false, columnDefinition = "TEXT")
    private String tipoReclamo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String pedido;

    @Column(length = 20, nullable = false)
    private String estado = "pendiente";

    @Column(columnDefinition = "TEXT")
    private String respuesta;

    @Column(name = "fecha_respuesta", columnDefinition = "TEXT")
    private String fechaRespuesta;

    @Column(name = "archivo_url", columnDefinition = "TEXT")
    private String archivoUrl;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
