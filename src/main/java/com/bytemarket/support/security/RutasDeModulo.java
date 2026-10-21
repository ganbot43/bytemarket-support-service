package com.bytemarket.support.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * A qué módulo del panel pertenece cada ruta de /api/admin de ESTE servicio.
 *
 * Es el mapa más corto de los cuatro: este servicio sirve una sola pantalla
 * del panel, el libro de reclamaciones. Aun así se declara en vez de dejar
 * pasar todo /api/admin, porque la regla "lo que no esté declarado no pasa"
 * es la que protege de lo que se añada mañana.
 */
public final class RutasDeModulo {

    private RutasDeModulo() {}

    private record Regla(String prefijo, List<String> modulos) {}

    private static final List<Regla> REGLAS = List.of(
            new Regla("/api/admin/reclamaciones", List.of("reclamaciones")));

    /** Los módulos que habilitan esta petición; vacío si la ruta no está declarada. */
    public static List<String> modulos(HttpServletRequest request) {
        String path = request.getRequestURI();
        List<String> mejor = List.of();
        int largo = -1;
        for (Regla r : REGLAS) {
            boolean casa = path.equals(r.prefijo()) || path.startsWith(r.prefijo() + "/");
            if (casa && r.prefijo().length() > largo) {
                mejor = r.modulos();
                largo = r.prefijo().length();
            }
        }
        return mejor;
    }

    /**
     * Qué acción pide esta petición, según el verbo.
     *
     * GET y HEAD consultan, POST da de alta, PUT y PATCH modifican, DELETE da
     * de baja. Sustituye a tener una regla por verbo en el SecurityConfig,
     * que es de donde salían los errores de "esta ruta se la llevó por
     * delante el comodín".
     *
     * Un POST que no crea nada —abrir la caja, cuadrar un turno— cuenta como
     * crear, y es lo correcto: son actos que dejan un registro nuevo, no
     * consultas.
     */
    public static String accion(HttpServletRequest request) {
        return switch (request.getMethod()) {
            case "POST" -> "create";
            case "PUT", "PATCH" -> "edit";
            case "DELETE" -> "delete";
            default -> "view";
        };
    }
}
