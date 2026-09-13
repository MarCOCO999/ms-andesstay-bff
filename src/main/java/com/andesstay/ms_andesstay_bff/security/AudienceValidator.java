package com.andesstay.ms_andesstay_bff.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Azure AD no valida el "aud" por si solo al resolver el issuer: hay que comprobarlo a mano
 * contra el clientId/App ID URI de la API registrada en el App Registration. Cuando el
 * Application ID URI es el default ("api://<clientId>"), Azure AD v2.0 a veces emite el
 * "aud" sin el prefijo "api://" (solo el GUID); se aceptan ambas formas.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token", "El token no contiene la audiencia esperada para esta API", null);

    private static final String API_PREFIX = "api://";

    private final Set<String> acceptedAudiences;

    public AudienceValidator(String expectedAudience) {
        Set<String> accepted = new LinkedHashSet<>();
        accepted.add(expectedAudience);
        if (expectedAudience.startsWith(API_PREFIX)) {
            accepted.add(expectedAudience.substring(API_PREFIX.length()));
        }
        this.acceptedAudiences = accepted;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(acceptedAudiences::contains)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
