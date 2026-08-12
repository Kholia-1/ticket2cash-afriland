package com.afriland.ticket2cash.auth;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PasswordPolicyService {

    private static final List<String> FORBIDDEN_PASSWORDS = List.of(
            "123456",
            "12345678",
            "password",
            "azerty",
            "qwerty",
            "admin",
            "test",
            "ticket2cash"
    );

    public String validate(String password, String username) {

        if (password == null || password.isBlank()) {
            return "Password is required";
        }

        if (password.length() < 8) {
            return "Password must contain at least 8 characters";
        }

        if (password.contains(" ")) {
            return "Password must not contain spaces";
        }

        boolean hasLetter = password.matches(".*[A-Za-z].*");
        boolean hasDigit = password.matches(".*[0-9].*");

        if (!hasLetter) {
            return "Password must contain at least one letter";
        }

        if (!hasDigit) {
            return "Password must contain at least one digit";
        }

        String lowerPassword = password.toLowerCase();

        if (FORBIDDEN_PASSWORDS.contains(lowerPassword)) {
            return "Password is too common";
        }

        if (username != null && !username.isBlank()) {
            String lowerUsername = username.toLowerCase();

            if (lowerPassword.equals(lowerUsername)) {
                return "Password must not be equal to username";
            }
        }

        return null;
    }
}