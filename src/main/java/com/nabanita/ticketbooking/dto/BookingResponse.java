package com.nabanita.ticketbooking.dto;

import com.nabanita.ticketbooking.entity.Booking;

import java.time.Instant;
import java.util.List;

public record BookingResponse(Long id, Long showId, List<Long> seatIds, String status, Instant createdAt) {
    public static BookingResponse from(Booking b) {
        return new BookingResponse(b.getId(), b.getShowId(), List.copyOf(b.getSeatIds()),
                b.getStatus().name(), b.getCreatedAt());
    }
}
