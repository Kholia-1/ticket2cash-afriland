package com.afriland.ticket2cash.mobile;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * OCR Controller — forwards images to the Python ImgOCR server.
 * Python server runs on port 5001 with ImgOCR (PaddleOCR v5 ONNX).
 */
@RestController
@RequestMapping("/api/mobile")
@CrossOrigin(origins = "*")
public class OcrController {

    private static final String OCR_SERVER = "http://localhost:5001/ocr";
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/ocr")
    public ResponseEntity<?> analyzeReceipt(@RequestBody Map<String, String> body) {
        String imageBase64 = body.get("image");

        if (imageBase64 == null || imageBase64.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ocr_success", false,
                "message", "No image provided"
            ));
        }

        try {
            // Forward to Python ImgOCR server
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> ocrBody = Map.of("image", imageBase64);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(ocrBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                OCR_SERVER, HttpMethod.POST, entity, Map.class
            );

            return ResponseEntity.ok(response.getBody());

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "ocr_success", false,
                "message", "OCR server not reachable. Start it with: python ocr_server.py | Error: " + e.getMessage()
            ));
        }
    }
}
