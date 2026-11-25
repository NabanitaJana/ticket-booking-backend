package com.nabanita.ticketbooking.dto;

public record TokenResponse(String accessToken, long expiresInSeconds) {}
