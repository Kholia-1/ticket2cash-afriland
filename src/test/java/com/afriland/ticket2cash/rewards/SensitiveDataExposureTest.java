package com.afriland.ticket2cash.rewards;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SensitiveDataExposureTest {
    private static final Pattern PAN = Pattern.compile("\\b\\d{13,19}\\b");

    @Test
    void frontendDoesNotContainAFullPan() throws Exception {
        Path root = Path.of("src/main/resources/static");
        if (!Files.exists(root)) return;
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".html") || path.toString().endsWith(".js"))
                    .forEach(path -> {
                        try {
                            assertFalse(PAN.matcher(Files.readString(path)).find(),
                                    "Full PAN found in " + path);
                        } catch (java.io.IOException e) {
                            throw new IllegalStateException(e);
                        }
                    });
        }
    }
}
