package com.nabanita.ticketbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** One row per emailed booking, so a redelivered Kafka message does not send a second email. */
@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long bookingId;

    @Column(nullable = false)
    private Instant sentAt = Instant.now();
}
