package com.nabanita.ticketbooking.dto;

import jakarta.validation.constraints.*;

import java.util.Set;

public record ConfirmRequest(
        @NotNull Long showId,
        @NotEmpty @Size(max = 10) Set<@NotNull Long> seatIds) {}
