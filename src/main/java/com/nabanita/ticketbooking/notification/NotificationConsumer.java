package com.nabanita.ticketbooking.notification;

import com.nabanita.ticketbooking.config.KafkaConfig;
import com.nabanita.ticketbooking.entity.Notification;
import com.nabanita.ticketbooking.event.BookingConfirmedEvent;
import com.nabanita.ticketbooking.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final EmailSender emailSender;
    private final NotificationRepository notificationRepo;

    /** Exceptions are retried, then dead-lettered, by the error handler in KafkaConfig. */
    @KafkaListener(topics = KafkaConfig.BOOKING_CONFIRMED)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        if (notificationRepo.existsByBookingId(event.bookingId())) {
            log.info("Booking {} already notified, skipping", event.bookingId());
            return;
        }
        emailSender.send(event.userEmail(), "Booking #" + event.bookingId() + " confirmed",
                "Your " + event.seatIds().size() + " seat(s) are booked.");
        Notification n = new Notification();
        n.setBookingId(event.bookingId());
        notificationRepo.save(n);
    }
}
