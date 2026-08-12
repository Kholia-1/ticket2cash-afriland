package com.afriland.ticket2cash.auth;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AuthInitializer implements CommandLineRunner {

    private final AuthService authService;
    private final AppUserRepository userRepository;

    public AuthInitializer(AuthService authService, AppUserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        // Admin account
        authService.createInitialUserIfMissing(
                "admin",
                "Administrateur Ticket2Cash",
                "admin@afrilandfirstbank.com",
                "admin123",
                UserRole.ADMIN
        );

        // Partner accounts — linked to merchants by merchantId
        // merchantId 1 = Santa Lucia Yaounde (created by DataInitializer)
        createPartnerIfMissing(
                "santalucia",
                "Gerant Santa Lucia",
                "santalucia@ticket2cash.local",
                "partner123",
                1L
        );

        // You can add more partner accounts here:
        // createPartnerIfMissing("dovv", "Gerant Dovv", "dovv@ticket2cash.local", "partner123", 2L);
    }

    private void createPartnerIfMissing(String username, String fullName,
                                         String email, String password,
                                         Long merchantId) {
        if (!userRepository.existsByUsername(username)) {
            authService.createInitialUserIfMissing(username, fullName, email, password, UserRole.PARTNER);
            // Set merchantId after creation
            userRepository.findByUsername(username).ifPresent(user -> {
                user.setMerchantId(merchantId);
                userRepository.save(user);
            });
            System.out.println("[AUTH] Partner account created: " + username + " -> merchantId=" + merchantId);
        }
    }
}
