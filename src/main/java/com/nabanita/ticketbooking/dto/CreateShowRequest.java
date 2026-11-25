package com.nabanita.ticketbooking.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record CreateShowRequest(
        @NotBlank String name,
        @NotBlank String venue,
        @NotNull @Future LocalDateTime startTime,
        @Min(1) @Max(500) int totalSeats) {}
