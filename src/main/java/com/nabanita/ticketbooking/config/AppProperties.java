package com.nabanita.ticketbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app")
public record AppProperties(Duration holdTtl, Jwt jwt, Admin admin) {
    public record Jwt(String secret, Duration ttl) {}
    public record Admin(String email, String password) {}
}
