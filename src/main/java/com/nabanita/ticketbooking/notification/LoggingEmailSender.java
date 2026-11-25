package com.nabanita.ticketbooking.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Mock sender. Swap in a JavaMailSender or SES implementation for real delivery. */
@Slf4j
@Component
public class LoggingEmailSender implements EmailSender {
    @Override
    public void send(String to, String subject, String body) {
        log.info("EMAIL to={} subject='{}' body='{}'", to, subject, body);
    }
}
