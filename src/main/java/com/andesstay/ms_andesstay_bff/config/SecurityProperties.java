package com.andesstay.ms_andesstay_bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "andesstay.security")
public record SecurityProperties(String audience, String rolesClaim, List<String> allowedOrigins) {
}
