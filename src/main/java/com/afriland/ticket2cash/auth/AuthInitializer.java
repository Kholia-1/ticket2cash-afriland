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
        String adminPassword = System.getenv("TICKET2CASH_ADMIN_PASSWORD");
        if (adminPassword != null && !adminPassword.isBlank()) {
            authService.createInitialUserIfMissing(
                "admin",
                "Administrateur Ticket2Cash",
                "admin@afrilandfirstbank.com",
                adminPassword,
                UserRole.ADMIN
            );
        }

        // Partner accounts — linked to merchants by merchantId
        // merchantId 1 = Santa Lucia Yaounde (created by DataInitializer)
        String partnerPassword = System.getenv("TICKET2CASH_PARTNER_PASSWORD");
        if (partnerPassword != null && !partnerPassword.isBlank()) {
            createPartnerIfMissing(
                "santalucia",
                "Gerant Santa Lucia",
                "santalucia@ticket2cash.local",
                partnerPassword,
                1L
            );
        }
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
        }
    }
}
