package com.nabanita.ticketbooking.service;

import com.nabanita.ticketbooking.config.KafkaConfig;
import com.nabanita.ticketbooking.event.BookingConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Side effects that must only happen once the booking is committed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventListener {

    private final SeatHoldService holdService;
    private final KafkaTemplate<Object, Object> kafka;

    @TransactionalEventListener
    public void releaseHold(BookingConfirmedEvent event) {
        try {
            holdService.release(event.showId(), event.seatIds(), event.userEmail());
        } catch (RuntimeException ex) { // the hold expires by itself; the booking already succeeded
            log.warn("Could not release hold for booking {}", event.bookingId(), ex);
        }
    }

    @Async // a slow or unavailable broker must not delay the booking response
    @TransactionalEventListener
    public void publish(BookingConfirmedEvent event) {
        kafka.send(KafkaConfig.BOOKING_CONFIRMED, event.bookingId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) log.error("Could not publish booking {}", event.bookingId(), ex);
                });
    }
}
