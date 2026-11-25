package com.nabanita.ticketbooking.service;

import com.nabanita.ticketbooking.dto.*;
import com.nabanita.ticketbooking.entity.*;
import com.nabanita.ticketbooking.event.BookingConfirmedEvent;
import com.nabanita.ticketbooking.exception.ApiException;
import com.nabanita.ticketbooking.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final ShowRepository showRepo;
    private final SeatRepository seatRepo;
    private final BookingRepository bookingRepo;
    private final SeatHoldService holdService;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    /** Step 1: temporarily hold seats in Redis. */
    public HoldResponse holdSeats(Long showId, Set<Long> seatIds, String user) {
        loadAvailableSeats(showId, seatIds);
        long ttlMillis = holdService.hold(showId, seatIds, user);
        if (ttlMillis == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "One or more seats are currently held by another user");
        }
        return new HoldResponse(showId, seatIds, (ttlMillis + 999) / 1000);
    }

    /**
     * Step 2: confirm the booking. Retrying with the same idempotency key returns the original
     * booking, including when both requests are in flight at once and this one loses the race.
     */
    public BookingResponse confirm(ConfirmRequest req, String idempotencyKey, String user) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be 1 to 100 characters");
        }
        Optional<BookingResponse> previous = findPrevious(req, idempotencyKey, user);
        if (previous.isPresent()) return previous.get();

        try {
            return BookingResponse.from(tx.execute(status -> createBooking(req, idempotencyKey, user)));
        } catch (RuntimeException ex) {
            return findPrevious(req, idempotencyKey, user).orElseThrow(() -> ex);
        }
    }

    public List<BookingResponse> myBookings(String user) {
        return bookingRepo.findByUserEmailOrderByIdDesc(user).stream().map(BookingResponse::from).toList();
    }

    private Booking createBooking(ConfirmRequest req, String idempotencyKey, String user) {
        List<Seat> seats = loadAvailableSeats(req.showId(), req.seatIds());
        if (!holdService.isHeldBy(req.showId(), req.seatIds(), user)) {
            throw new ApiException(HttpStatus.CONFLICT, "Hold expired or not owned by you");
        }

        Booking booking = new Booking();
        booking.setShowId(req.showId());
        booking.setUserEmail(user);
        booking.setSeatIds(sorted(req.seatIds()));
        booking.setIdempotencyKey(idempotencyKey);
        bookingRepo.save(booking);

        for (Seat s : seats) { // dirty checking flushes these with a @Version check
            s.setStatus(SeatStatus.BOOKED);
            s.setBookingId(booking.getId());
        }

        // Delivered to the listeners only after the commit succeeds
        events.publishEvent(new BookingConfirmedEvent(
                booking.getId(), req.showId(), user, sorted(req.seatIds())));
        return booking;
    }

    private Optional<BookingResponse> findPrevious(ConfirmRequest req, String idempotencyKey, String user) {
        Optional<Booking> booking = bookingRepo.findByUserEmailAndIdempotencyKey(user, idempotencyKey);
        booking.ifPresent(b -> {
            boolean sameRequest = b.getShowId().equals(req.showId())
                    && sorted(b.getSeatIds()).equals(sorted(req.seatIds()));
            if (!sameRequest) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "Idempotency-Key was already used for a different request");
            }
        });
        return booking.map(BookingResponse::from);
    }

    private List<Seat> loadAvailableSeats(Long showId, Set<Long> seatIds) {
        Show show = showRepo.findById(showId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Show not found"));
        if (!show.getStartTime().isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Show has already started");
        }
        List<Seat> seats = seatRepo.findByShowIdAndIdInOrderById(showId, seatIds);
        if (seats.size() != seatIds.size()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Some seats do not exist for this show");
        }
        for (Seat s : seats) {
            if (s.getStatus() != SeatStatus.AVAILABLE) {
                throw new ApiException(HttpStatus.CONFLICT, "Seat " + s.getSeatNumber() + " already booked");
            }
        }
        return seats;
    }

    private List<Long> sorted(Collection<Long> ids) {
        return ids.stream().sorted().toList();
    }
}
