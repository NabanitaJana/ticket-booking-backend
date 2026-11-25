package com.nabanita.ticketbooking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    public static final String BOOKING_CONFIRMED = "booking-confirmed";
    public static final String BOOKING_CONFIRMED_DLT = BOOKING_CONFIRMED + "-dlt";

    @Bean
    NewTopic bookingConfirmedTopic() {
        return TopicBuilder.name(BOOKING_CONFIRMED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic bookingConfirmedDlt() {
        return TopicBuilder.name(BOOKING_CONFIRMED_DLT).partitions(3).replicas(1).build();
    }

    /** One attempt plus three retries, one second apart, then the dead-letter topic. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> operations) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(operations), new FixedBackOff(1000, 3));
    }
}
