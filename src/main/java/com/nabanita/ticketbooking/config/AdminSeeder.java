package com.nabanita.ticketbooking.config;

import com.nabanita.ticketbooking.entity.AppUser;
import com.nabanita.ticketbooking.entity.Role;
import com.nabanita.ticketbooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    @Override
    public void run(String... args) {
        String email = props.admin().email().toLowerCase();
        if (userRepo.existsByEmail(email)) return;
        AppUser admin = new AppUser();
        admin.setEmail(email);
        admin.setPasswordHash(encoder.encode(props.admin().password()));
        admin.setRole(Role.ADMIN);
        userRepo.save(admin);
    }
}
