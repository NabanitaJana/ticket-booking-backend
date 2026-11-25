package com.nabanita.ticketbooking.event;

import java.util.List;

public record BookingConfirmedEvent(Long bookingId, Long showId, String userEmail, List<Long> seatIds) {}
