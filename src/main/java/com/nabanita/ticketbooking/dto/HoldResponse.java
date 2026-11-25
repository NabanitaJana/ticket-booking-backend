package com.nabanita.ticketbooking.dto;

import java.util.Set;

public record HoldResponse(Long showId, Set<Long> seatIds, long expiresInSeconds) {}
