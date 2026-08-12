package com.afriland.ticket2cash.mobile;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.util.Base64;

/**
 * OCR Controller — forwards images to the Python ImgOCR server.
 * Python server runs on port 5001 with ImgOCR (PaddleOCR v5 ONNX).
 */
@RestController
@RequestMapping("/api/mobile")
public class OcrController {

    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private final String ocrServer;
    private final RestTemplate restTemplate;

    public OcrController(@Value("${ocr.server.url:http://localhost:5001/ocr}") String ocrServer) {
        this.ocrServer = ocrServer;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostMapping("/ocr")
    public ResponseEntity<?> analyzeReceipt(@RequestBody Map<String, String> body) {
        String imageBase64 = body.get("image");

        if (imageBase64 == null || imageBase64.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ocr_success", false,
                "message", "No image provided"
            ));
        }

        if (imageBase64.length() > (MAX_IMAGE_BYTES * 4 / 3) + 256) {
            return ResponseEntity.badRequest().body(Map.of(
                "ocr_success", false,
                "message", "Image is too large (maximum 10 MB)"
            ));
        }
        try {
            if (Base64.getDecoder().decode(imageBase64).length > MAX_IMAGE_BYTES) {
                return ResponseEntity.badRequest().body(Map.of(
                    "ocr_success", false,
                    "message", "Image is too large (maximum 10 MB)"
                ));
            }
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "ocr_success", false,
                "message", "Image must be valid Base64"
            ));
        }

        try {
            // Forward to Python ImgOCR server
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> ocrBody = Map.of("image", imageBase64);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(ocrBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                ocrServer, HttpMethod.POST, entity, Map.class
            );

            return ResponseEntity.ok(response.getBody());

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "ocr_success", false,
                "message", "OCR service is temporarily unavailable"
            ));
        }
    }
}
