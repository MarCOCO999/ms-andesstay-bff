package com.andesstay.ms_andesstay_bff.config;

import com.andesstay.ms_andesstay_bff.security.AudienceValidator;
import com.andesstay.ms_andesstay_bff.security.AzureRolesConverter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final String issuerUri;
    private final SecurityProperties securityProperties;

    public SecurityConfig(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
                           SecurityProperties securityProperties) {
        this.issuerUri = issuerUri;
        this.securityProperties = securityProperties;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(securityProperties.audience());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new AzureRolesConverter(securityProperties.rolesClaim()));
        return converter;
    }

    /**
     * El navegador bloquea las llamadas del frontend Angular (localhost:4200 en dev, el
     * dominio real desplegado en produccion) a menos que el BFF conteste el preflight
     * OPTIONS con los headers Access-Control-Allow-*. curl no lo necesita porque CORS es
     * una restriccion que solo aplica en el navegador, por eso no se detecto antes.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
                                            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // Catalogo: solo Admin gestiona inventario/tarifas; el resto de roles solo lectura
                        .requestMatchers(HttpMethod.POST, "/api/catalog/**").hasRole("Admin")
                        .requestMatchers(HttpMethod.PUT, "/api/catalog/**").hasRole("Admin")
                        .requestMatchers(HttpMethod.GET, "/api/catalog/**").hasAnyRole("Admin", "Operador", "Cliente")

                        // Reservas: cambio de estado solo Operador/Admin; creacion y lectura, todos los roles de negocio
                        .requestMatchers(HttpMethod.PUT, "/api/reservations/*/status").hasAnyRole("Admin", "Operador")
                        .requestMatchers(HttpMethod.POST, "/api/reservations").hasAnyRole("Admin", "Operador", "Cliente")
                        .requestMatchers(HttpMethod.GET, "/api/reservations/**").hasAnyRole("Admin", "Operador", "Cliente")

                        // Reportes: solo Admin
                        .requestMatchers("/api/report/**").hasRole("Admin")

                        // Auditoria: solo lectura para Admin y Auditor
                        .requestMatchers(HttpMethod.GET, "/api/audit/**").hasAnyRole("Admin", "Auditor")

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(this::handleUnauthenticated)
                        .accessDeniedHandler(this::handleAccessDenied)
                );
        return http.build();
    }

    private void handleUnauthenticated(jakarta.servlet.http.HttpServletRequest request,
                                        HttpServletResponse response,
                                        org.springframework.security.core.AuthenticationException ex) throws java.io.IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "No autenticado: token ausente, invalido o vencido");
    }

    private void handleAccessDenied(jakarta.servlet.http.HttpServletRequest request,
                                     HttpServletResponse response,
                                     org.springframework.security.access.AccessDeniedException ex) throws java.io.IOException {
        writeError(response, HttpServletResponse.SC_FORBIDDEN, "El rol del usuario no tiene permiso para este recurso");
    }

    private void writeError(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        String escaped = message.replace("\"", "'");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + escaped + "\"}");
    }
}
