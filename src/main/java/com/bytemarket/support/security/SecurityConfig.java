package com.bytemarket.support.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * - /api/admin/**   según la matriz de permisos (ModuloAuthorizationManager)
 * - /api/internal/** llamadas entre microservicios (no se exponen en el gateway)
 * - resto: público; los endpoints de cliente validan la sesión en el controlador
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * El filtro llega como parámetro del @Bean y no como campo inyectado:
     * una clase @Configuration que además produce beans puede entrar en
     * conflicto de creación si se inyecta por constructor, y de este modo
     * la dependencia queda donde se usa.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthFilter,
                                                   ModuloAuthorizationManager porModulo) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                /*
                 * /api/admin lo decide la MATRIZ de permisos, no un rol
                 * escrito aquí: si no, quitarle el libro de reclamaciones a
                 * alguien desde la pantalla de Roles y permisos escondería el
                 * menú y no cerraría la API.
                 */
                .requestMatchers("/api/admin/**").access(porModulo)
                .anyRequest().permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> json(res, HttpServletResponse.SC_UNAUTHORIZED, "No autenticado"))
                .accessDeniedHandler((req, res, e) -> json(res, HttpServletResponse.SC_FORBIDDEN, "Acceso denegado"))
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void json(HttpServletResponse res, int status, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"statusCode\":" + status + ",\"message\":\"" + message + "\"}");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
