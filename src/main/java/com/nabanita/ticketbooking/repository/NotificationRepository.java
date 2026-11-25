package com.nabanita.ticketbooking.repository;

import com.nabanita.ticketbooking.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByBookingId(Long bookingId);
}
