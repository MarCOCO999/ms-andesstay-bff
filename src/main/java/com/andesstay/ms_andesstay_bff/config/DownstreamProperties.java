package com.andesstay.ms_andesstay_bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "andesstay.downstream")
public record DownstreamProperties(String reservationsUrl, String catalogUrl, String auditUrl, String reportUrl,
                                   String rabbitAdminUrl) {

    /**
     * Mapea el primer segmento de path (/api/{domain}/**) a la URL base del microservicio de dominio.
     */
    public Map<String, String> asRouteTable() {
        return Map.of(
                "reservations", reservationsUrl,
                "catalog", catalogUrl,
                "audit", auditUrl,
                "report", reportUrl,
                "rabbit", rabbitAdminUrl
        );
    }
}
