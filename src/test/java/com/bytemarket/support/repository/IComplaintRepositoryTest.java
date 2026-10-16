package com.bytemarket.support.repository;

import com.bytemarket.support.model.Complaint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de la capa de acceso a datos del Libro de Reclamaciones.
 *
 * @DataJpaTest levanta solo JPA contra una base en memoria y revierte la
 * transacción al final de cada prueba, de modo que no se toca MySQL ni
 * importa el orden de ejecución.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("IComplaintRepository — capa de acceso a datos")
class IComplaintRepositoryTest {

    @Autowired
    private IComplaintRepository repositorio;

    @Autowired
    private TestEntityManager em;

    private Complaint nuevoReclamo(String codigo, String nombre, String tipoReclamo) {
        Complaint c = new Complaint();
        c.setCodigo(codigo);
        c.setCustomerName(nombre);
        c.setTipoDocumento("DNI");
        c.setNumeroDocumento("12345678");
        c.setDireccion("Av. Siempre Viva 123, Lima");
        c.setTelefono("+51 999 999 999");
        c.setEmail("cliente@ejemplo.com");
        c.setTipoBien("producto");
        c.setDescripcionBien("Pantalla OLED Samsung S22");
        c.setMonto(249.90);
        c.setTipoReclamo(tipoReclamo);
        c.setDescripcion("La pieza recibida no corresponde al modelo solicitado.");
        c.setPedido("PED-000123");
        c.setEstado("pendiente");
        return c;
    }

    @BeforeEach
    void limpiar() {
        repositorio.deleteAll();
        em.flush();
    }

    // ── INSERTAR ─────────────────────────────────────────────────────

    @Test
    @DisplayName("insertar: persiste el reclamo con id generado y fecha de creación")
    void insertar() {
        Complaint guardado = repositorio.save(nuevoReclamo("REC-0001", "Juan Pérez", "reclamo"));
        em.flush();
        em.clear();

        assertNotNull(guardado.getId());

        Complaint desdeLaBase = em.find(Complaint.class, guardado.getId());
        assertNotNull(desdeLaBase);
        assertEquals("REC-0001", desdeLaBase.getCodigo());
        assertEquals("Juan Pérez", desdeLaBase.getCustomerName());
        assertEquals("pendiente", desdeLaBase.getEstado(), "todo reclamo nace pendiente");
        assertNotNull(desdeLaBase.getCreatedAt(),
                "la fecha es parte del respaldo legal del Libro de Reclamaciones");
    }

    @Test
    @DisplayName("insertar: la descripción admite un texto largo por ser columna de texto")
    void insertarDescripcionLarga() {
        Complaint c = nuevoReclamo("REC-0002", "María López", "queja");
        /* El consumidor puede extenderse: la ley no limita la longitud del
           detalle, y por eso la columna es TEXT y no un VARCHAR corto. */
        c.setDescripcion("Detalle extenso del reclamo. ".repeat(60));

        Complaint guardado = repositorio.save(c);
        em.flush();
        em.clear();

        assertTrue(em.find(Complaint.class, guardado.getId()).getDescripcion().length() > 1000);
    }

    // ── ACTUALIZAR ───────────────────────────────────────────────────

    @Test
    @DisplayName("actualizar: registra la respuesta y cambia el estado sin crear fila nueva")
    void actualizar() {
        Complaint original = repositorio.save(nuevoReclamo("REC-0003", "Carlos Ruiz", "reclamo"));
        em.flush();
        Integer id = original.getId();

        original.setEstado("atendido");
        original.setRespuesta("Se aprobó el cambio de la pieza y se despachó el reemplazo.");
        original.setFechaRespuesta("2026-10-15");
        repositorio.save(original);
        em.flush();
        em.clear();

        Complaint actualizado = em.find(Complaint.class, id);
        assertEquals("atendido", actualizado.getEstado());
        assertNotNull(actualizado.getRespuesta());
        assertEquals("2026-10-15", actualizado.getFechaRespuesta());
        assertEquals(1, repositorio.count(), "actualizar no debe insertar una fila adicional");
    }

    // ── ELIMINAR ─────────────────────────────────────────────────────

    @Test
    @DisplayName("eliminar: borra la fila y deja de encontrarse por id")
    void eliminar() {
        Complaint c = repositorio.save(nuevoReclamo("REC-0004", "Ana Torres", "queja"));
        em.flush();
        Integer id = c.getId();

        repositorio.deleteById(id);
        em.flush();
        em.clear();

        assertFalse(repositorio.findById(id).isPresent());
        assertNull(em.find(Complaint.class, id));
        assertEquals(0, repositorio.count());
    }

    // ── LISTAR ───────────────────────────────────────────────────────

    @Test
    @DisplayName("listar: devuelve todos los reclamos persistidos")
    void listar() {
        repositorio.save(nuevoReclamo("REC-0005", "Cliente Uno", "reclamo"));
        repositorio.save(nuevoReclamo("REC-0006", "Cliente Dos", "queja"));
        repositorio.save(nuevoReclamo("REC-0007", "Cliente Tres", "reclamo"));
        em.flush();
        em.clear();

        List<Complaint> todos = repositorio.findAll();
        assertEquals(3, todos.size());
        assertTrue(todos.stream().anyMatch(c -> "REC-0006".equals(c.getCodigo())));
    }

    @Test
    @DisplayName("listar: findById recupera el reclamo y distingue el inexistente")
    void listarPorId() {
        Complaint guardado = repositorio.save(nuevoReclamo("REC-0008", "Cliente Buscado", "reclamo"));
        em.flush();
        em.clear();

        assertTrue(repositorio.findById(guardado.getId()).isPresent());
        assertTrue(repositorio.findById(999999).isEmpty(),
                "un id ausente debe devolver vacío, no lanzar");
    }
}
