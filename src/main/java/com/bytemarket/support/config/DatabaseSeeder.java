package com.bytemarket.support.config;

import com.bytemarket.support.model.Complaint;
import com.bytemarket.support.repository.IComplaintRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeder de ByteMarket (support-service): reclamos de ejemplo para el Libro de Reclamaciones.
 */
@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    private IComplaintRepository complaintRepository;

    @Override
    public void run(String... args) {
        if (complaintRepository.count() > 0) return;
        System.out.println("🌱 Seeding Libro de Reclamaciones (ByteMarket)...");

        complaintRepository.save(build("REC-2026-0001", "Cliente Demo", "DNI", "71234567",
                "Av. Javier Prado Este 1234, San Isidro", "987654321", "cliente@bytemarket.com",
                "Producto", "Tarjeta gráfica RTX 4060 8GB", 1399.0, "Reclamo",
                "El producto llegó con la caja dañada y un ventilador no gira.", "BM-000001",
                "pendiente", null, null));

        complaintRepository.save(build("REC-2026-0002", "María López", "DNI", "72345678",
                "Jr. de la Unión 456, Cercado de Lima", "912345678", "maria.lopez@bytemarket.com",
                "Servicio", "Envío a domicilio", 15.0, "Queja",
                "El delivery demoró 3 días más de lo indicado.", "BM-000002",
                "respondido", "Lamentamos la demora. Se reembolsó el costo de envío.", "2026-10-01"));

        complaintRepository.save(build("REC-2026-0003", "Carlos Ramírez", "CE", "001234567",
                "Av. Arequipa 2450, Lince", "934567812", "carlos.ramirez@gmail.com",
                "Producto", "Teclado mecánico RGB", 189.9, "Reclamo",
                "Varias teclas dejaron de funcionar a la semana de uso.", "BM-000003",
                "en_proceso", null, null));

        System.out.println("✅ Reclamos de ejemplo creados.");
    }

    private Complaint build(String codigo, String name, String tipoDoc, String numDoc, String dir, String tel,
                            String email, String tipoBien, String descBien, Double monto, String tipoReclamo,
                            String desc, String pedido, String estado, String respuesta, String fechaResp) {
        Complaint c = new Complaint();
        c.setCodigo(codigo);
        c.setCustomerName(name);
        c.setTipoDocumento(tipoDoc);
        c.setNumeroDocumento(numDoc);
        c.setDireccion(dir);
        c.setTelefono(tel);
        c.setEmail(email);
        c.setTipoBien(tipoBien);
        c.setDescripcionBien(descBien);
        c.setMonto(monto);
        c.setTipoReclamo(tipoReclamo);
        c.setDescripcion(desc);
        c.setPedido(pedido);
        c.setEstado(estado);
        c.setRespuesta(respuesta);
        c.setFechaRespuesta(fechaResp);
        return c;
    }
}
