package com.afriland.ticket2cash.auth;

import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class AuthService {

    private final AppUserRepository userRepository;

    public AuthService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AppUser createUser(String username,
                              String fullName,
                              String email,
                              String password,
                              UserRole role) {

        String salt = generateSalt();
        String hash = hashPassword(password, salt);

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordSalt(salt);
        user.setPasswordHash(hash);
        user.setRole(role);
        user.setEnabled(true);

        return userRepository.save(user);
    }

    public boolean verifyPassword(String rawPassword, AppUser user) {
        String rawHash = hashPassword(rawPassword, user.getPasswordSalt());

        return MessageDigest.isEqual(
                rawHash.getBytes(),
                user.getPasswordHash().getBytes()
        );
    }

    public AppUser changePassword(AppUser user, String newPassword) {
        String salt = generateSalt();
        String hash = hashPassword(newPassword, salt);

        user.setPasswordSalt(salt);
        user.setPasswordHash(hash);

        return userRepository.save(user);
    }

    public void updateLastLogin(AppUser user) {
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void createInitialUserIfMissing(String username,
                                           String fullName,
                                           String email,
                                           String password,
                                           UserRole role) {

        if (!userRepository.existsByUsername(username)) {
            createUser(username, fullName, email, password, role);
        }
    }

    private String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hashPassword(String password, String salt) {
        try {
            byte[] saltBytes = Base64.getDecoder().decode(salt);

            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    saltBytes,
                    65536,
                    256
            );

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();

            return Base64.getEncoder().encodeToString(hash);

        } catch (Exception e) {
            throw new RuntimeException("Password hashing error", e);
        }
    }
}