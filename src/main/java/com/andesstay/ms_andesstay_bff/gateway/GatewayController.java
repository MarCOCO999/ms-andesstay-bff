package com.andesstay.ms_andesstay_bff.gateway;

import com.andesstay.ms_andesstay_bff.config.DownstreamProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.Set;

/**
 * Punto unico de entrada tras el API Gateway: una vez que Spring Security valido firma,
 * issuer, audience, vigencia y rol del JWT, este controlador reenvia la peticion tal cual
 * (mismo path, query, body y el mismo Bearer token) al microservicio de dominio que
 * corresponda, para que este vuelva a validar el JWT de forma independiente (defensa en
 * profundidad, JWT -> API Gateway -> BFF -> microservicio de dominio).
 */
@RestController
public class GatewayController {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "host", "content-length", "connection", "transfer-encoding", "keep-alive",
            "upgrade", "proxy-authenticate", "proxy-authorization", "te", "trailer");

    private final DownstreamProperties downstreamProperties;
    private final RestClient.Builder restClientBuilder;

    public GatewayController(DownstreamProperties downstreamProperties, RestClient.Builder restClientBuilder) {
        this.downstreamProperties = downstreamProperties;
        this.restClientBuilder = restClientBuilder;
    }

    @RequestMapping(
            path = "/api/{domain}/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE}
    )
    public ResponseEntity<byte[]> proxy(@PathVariable String domain,
                                         HttpServletRequest request,
                                         @RequestBody(required = false) byte[] body) {
        String baseUrl = downstreamProperties.asRouteTable().get(domain);
        if (baseUrl == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No existe un microservicio configurado para /api/" + domain);
        }

        URI targetUri = UriComponentsBuilder.fromUriString(baseUrl)
                .path(request.getRequestURI())
                .query(request.getQueryString())
                .build(true)
                .toUri();

        RestClient.RequestBodySpec spec = restClientBuilder.build()
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(targetUri)
                .headers(headers -> copyHeaders(request, headers));

        if (body != null && body.length > 0) {
            spec.body(body);
        }

        try {
            return spec.exchange((clientRequest, clientResponse) -> {
                HttpStatusCode status = clientResponse.getStatusCode();
                byte[] responseBody = clientResponse.bodyTo(byte[].class);
                return ResponseEntity.status(status)
                        .headers(filterResponseHeaders(clientResponse.getHeaders()))
                        .body(responseBody);
            });
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "El microservicio de dominio '" + domain + "' no respondio correctamente", ex);
        }
    }

    private void copyHeaders(HttpServletRequest request, HttpHeaders target) {
        Collections.list(request.getHeaderNames()).forEach(name -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                Collections.list(request.getHeaders(name)).forEach(value -> target.add(name, value));
            }
        });
    }

    private HttpHeaders filterResponseHeaders(HttpHeaders source) {
        HttpHeaders filtered = new HttpHeaders();
        source.forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                filtered.addAll(name, values);
            }
        });
        return filtered;
    }
}
