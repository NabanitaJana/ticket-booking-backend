package com.nabanita.ticketbooking;

import com.nabanita.ticketbooking.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class TicketbookingApplication {

    public static void main(String[] args) {
        // All times are UTC. Also avoids PostgreSQL rejecting legacy zone names such as Asia/Calcutta.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(TicketbookingApplication.class, args);
    }
}
