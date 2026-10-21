package com.bytemarket.support.security;

import com.bytemarket.support.security.PermissionsCache.Permiso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * El portero de /api/admin en soporte.
 *
 * Este servicio sirve una sola pantalla del panel, el libro de
 * reclamaciones, y antes estaba abierta a cualquier ADMIN por una regla
 * escrita aquí. Lo que se comprueba es que ahora manda la matriz.
 */
@DisplayName("Permisos — el portero de /api/admin en soporte")
class ModuloAuthorizationManagerTest {

    private PermissionsCache cache;
    private ModuloAuthorizationManager portero;

    private static final Permiso NADA = new Permiso(false, false, false, false);
    private static final Permiso VER = new Permiso(true, false, false, false);
    private static final Permiso TODO = new Permiso(true, true, true, true);

    @BeforeEach
    void setUp() {
        cache = mock(PermissionsCache.class);
        when(cache.permiso(any(), any())).thenReturn(NADA);
        portero = new ModuloAuthorizationManager(cache);
    }

    private boolean permite(String rol, String metodo, String ruta) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "quien@bytemarket.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase())));
        var decision = portero.check(() -> auth,
                new RequestAuthorizationContext(new MockHttpServletRequest(metodo, ruta)));
        return decision != null && decision.isGranted();
    }

    @Test
    @DisplayName("el encargado atiende las reclamaciones")
    void elEncargadoAtiendeReclamaciones() {
        when(cache.permiso(eq("admin"), eq("reclamaciones"))).thenReturn(TODO);

        assertTrue(permite("admin", "GET", "/api/admin/reclamaciones"));
        assertTrue(permite("admin", "GET", "/api/admin/reclamaciones/5"));
        assertTrue(permite("admin", "PUT", "/api/admin/reclamaciones/5"));
    }

    @Test
    @DisplayName("con solo ver se consulta la reclamación pero no se responde")
    void verNoAlcanzaParaResponder() {
        /* Es un caso con sentido de negocio: el libro de reclamaciones es un
           registro legal, y dejar consultarlo no es lo mismo que dejar
           cambiar el estado de un reclamo. */
        when(cache.permiso(eq("jefe_ventas"), eq("reclamaciones"))).thenReturn(VER);

        assertTrue(permite("jefe_ventas", "GET", "/api/admin/reclamaciones"));
        assertFalse(permite("jefe_ventas", "PUT", "/api/admin/reclamaciones/5"));
    }

    @Test
    @DisplayName("sin el módulo no se entra")
    void sinElModuloNoSeEntra() {
        assertFalse(permite("cajero", "GET", "/api/admin/reclamaciones"));
        assertFalse(permite("jefe_ventas", "GET", "/api/admin/reclamaciones"));
    }

    @Test
    @DisplayName("el gerente entra sin consultar la matriz")
    void elGerenteEntraATodo() {
        assertTrue(permite("superadmin", "PUT", "/api/admin/reclamaciones/5"));
        verify(cache, never()).permiso(any(), any());
    }

    @Test
    @DisplayName("una ruta de /api/admin sin módulo declarado NO pasa")
    void deniegaPorOmision() {
        assertFalse(permite("admin", "GET", "/api/admin/modulo-que-nadie-declaro"));
    }

    @Test
    @DisplayName("el token de servicio no entra por /api/admin, y sin sesión tampoco")
    void losDeFueraNoEntran() {
        assertFalse(permite("service", "GET", "/api/admin/reclamaciones"));
        assertFalse(permite("customer", "GET", "/api/admin/reclamaciones"));
        var decision = portero.check(() -> null,
                new RequestAuthorizationContext(new MockHttpServletRequest("GET", "/api/admin/reclamaciones")));
        assertFalse(decision.isGranted());
    }
}
