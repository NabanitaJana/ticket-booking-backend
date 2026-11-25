package com.nabanita.ticketbooking.repository;

import com.nabanita.ticketbooking.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByShowIdOrderById(Long showId);
    List<Seat> findByShowIdAndIdInOrderById(Long showId, Collection<Long> ids);
}
