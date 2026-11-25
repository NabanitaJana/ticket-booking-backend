package com.nabanita.ticketbooking.controller;

import com.nabanita.ticketbooking.dto.*;
import com.nabanita.ticketbooking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/shows/{showId}/hold")
    public HoldResponse hold(@PathVariable Long showId, @Valid @RequestBody HoldRequest req,
                             @AuthenticationPrincipal Jwt jwt) {
        return bookingService.holdSeats(showId, req.seatIds(), jwt.getSubject());
    }

    @PostMapping("/bookings")
    public BookingResponse confirm(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                   @Valid @RequestBody ConfirmRequest req,
                                   @AuthenticationPrincipal Jwt jwt) {
        return bookingService.confirm(req, idempotencyKey, jwt.getSubject());
    }

    @GetMapping("/bookings")
    public List<BookingResponse> myBookings(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.myBookings(jwt.getSubject());
    }
}
