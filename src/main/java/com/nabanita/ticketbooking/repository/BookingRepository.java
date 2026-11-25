package com.nabanita.ticketbooking.repository;

import com.nabanita.ticketbooking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByUserEmailAndIdempotencyKey(String userEmail, String idempotencyKey);
    List<Booking> findByUserEmailOrderByIdDesc(String userEmail);
}
