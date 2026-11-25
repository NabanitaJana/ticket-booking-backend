package com.nabanita.ticketbooking.dto;

import jakarta.validation.constraints.*;

import java.util.Set;

public record HoldRequest(@NotEmpty @Size(max = 10) Set<@NotNull Long> seatIds) {}
