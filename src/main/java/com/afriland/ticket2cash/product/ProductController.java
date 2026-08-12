package com.afriland.ticket2cash.product;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final MerchantRepository merchantRepository;
    private final AuditLogService auditLogService;

    public ProductController(ProductRepository productRepository,
                             MerchantRepository merchantRepository,
                             AuditLogService auditLogService) {
        this.productRepository = productRepository;
        this.merchantRepository = merchantRepository;
        this.auditLogService = auditLogService;
    }

    /** If the caller is a PARTNER, returns their own merchantId from the session; otherwise null. */
    private Long partnerMerchantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        if (!"PARTNER".equals(String.valueOf(session.getAttribute("AUTH_ROLE")))) return null;
        Object mid = session.getAttribute("AUTH_MERCHANT_ID");
        if (mid instanceof Number) return ((Number) mid).longValue();
        return null;
    }

    // ---------------------------------------------------------------- validation

    /**
     * Validates a product name against the "real product name" heuristic.
     * A product name must:
     *   - be non-blank
     *   - be at least 2 characters after trimming
     *   - contain at least one letter (a-z or accented) — rejects "@", "?", "!", "123"
     *   - not exceed 200 characters
     * Returns null when valid, or a human-readable error message otherwise.
     */
    private String validateProductName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "Le nom du produit est requis";
        }
        String trimmed = raw.trim();
        if (trimmed.length() < 2) {
            return "Le nom doit contenir au moins 2 caractères";
        }
        if (trimmed.length() > 200) {
            return "Le nom ne doit pas dépasser 200 caractères";
        }
        // Must contain at least one letter (Latin or accented). Prevents "@@", "###", "42".
        if (!trimmed.matches(".*[\\p{L}].*")) {
            return "Le nom doit contenir au moins une lettre (ex: 'Shampoing', pas '@' ou '?')";
        }
        return null;
    }

    /** SKU validation — must be non-blank if provided, but SKU itself is optional. */
    private String validateSku(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > 60) return "Le SKU ne doit pas dépasser 60 caractères";
        return null;
    }

    // ---------------------------------------------------------------- read

    @GetMapping
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @GetMapping("/merchant/{merchantId}")
    public List<Product> getProductsByMerchant(@PathVariable Long merchantId) {
        return productRepository.findByMerchantId(merchantId);
    }

    // ---------------------------------------------------------------- create

    @PostMapping
    public ResponseEntity<?> createProduct(@RequestBody ProductRequest request, HttpServletRequest http) {

        // Validate the name FIRST — before touching merchant / DB / audit
        String nameError = validateProductName(request.getName());
        if (nameError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", nameError, "field", "name"));
        }
        String skuError = validateSku(request.getSku());
        if (skuError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", skuError, "field", "sku"));
        }

        // A partner can only create products for their own merchant (enforced server-side).
        Long effMerchantId = request.getMerchantId();
        Long partnerMid = partnerMerchantId(http);
        if (partnerMid != null) effMerchantId = partnerMid;

        Merchant merchant = effMerchantId != null ? merchantRepository.findById(effMerchantId).orElse(null) : null;

        if (merchant == null) {
            auditLogService.log("CREATE_PRODUCT_FAILED", "PRODUCT", "Product", null,
                    "ADMIN_DEMO", "FAILED", "Merchant not found: " + effMerchantId);
            return ResponseEntity.badRequest().body(Map.of("error", "Commerçant introuvable"));
        }

        Product product = new Product();
        product.setMerchant(merchant);
        product.setSku(request.getSku() != null ? request.getSku().trim() : null);
        product.setName(request.getName().trim());
        product.setTicketDesignation(request.getTicketDesignation());
        product.setSynonyms(request.getSynonyms());
        product.setCategory(request.getCategory());
        product.setBrand(request.getBrand());
        product.setPrice(request.getPrice());
        product.setGroupKey(request.getGroupKey());
        product.setCashbackType(request.getCashbackType() != null ? request.getCashbackType() : CashbackType.NONE);
        product.setCashbackValue(request.getCashbackValue() != null ? request.getCashbackValue() : BigDecimal.ZERO);
        product.setActive(request.getActive() != null ? request.getActive() : true);

        Product savedProduct = productRepository.save(product);

        auditLogService.log("CREATE_PRODUCT", "PRODUCT", "Product", savedProduct.getId(),
                "ADMIN_DEMO", "SUCCESS", "Product created: " + savedProduct.getName());

        return ResponseEntity.ok(savedProduct);
    }

    // ---------------------------------------------------------------- update

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody ProductRequest request, HttpServletRequest http) {

        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }

        // A partner can only modify products that belong to their own merchant.
        Long partnerMid = partnerMerchantId(http);
        if (partnerMid != null) {
            Long owner = product.getMerchant() != null ? product.getMerchant().getId() : null;
            if (!partnerMid.equals(owner)) {
                return ResponseEntity.status(403).body(Map.of("error", "Ce produit n'appartient pas à votre commerçant."));
            }
        } else if (request.getMerchantId() != null) {
            Merchant merchant = merchantRepository.findById(request.getMerchantId()).orElse(null);
            if (merchant == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Commerçant introuvable"));
            }
            product.setMerchant(merchant);
        }

        // Validate name if it's being updated (not just partial patch)
        if (request.getName() != null) {
            String err = validateProductName(request.getName());
            if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "name"));
            product.setName(request.getName().trim());
        }
        if (request.getSku() != null) {
            String err = validateSku(request.getSku());
            if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "sku"));
            product.setSku(request.getSku().trim());
        }

        if (request.getTicketDesignation() != null) product.setTicketDesignation(request.getTicketDesignation());
        if (request.getSynonyms() != null) product.setSynonyms(request.getSynonyms());
        if (request.getCategory() != null) product.setCategory(request.getCategory());
        if (request.getBrand() != null) product.setBrand(request.getBrand());
        if (request.getPrice() != null) product.setPrice(request.getPrice());
        if (request.getGroupKey() != null) product.setGroupKey(request.getGroupKey());
        if (request.getCashbackType() != null) product.setCashbackType(request.getCashbackType());
        if (request.getCashbackValue() != null) product.setCashbackValue(request.getCashbackValue());
        if (request.getActive() != null) product.setActive(request.getActive());

        Product saved = productRepository.save(product);

        auditLogService.log("UPDATE_PRODUCT", "PRODUCT", "Product", saved.getId(),
                "ADMIN_DEMO", "SUCCESS", "Product updated: " + saved.getName());

        return ResponseEntity.ok(saved);
    }

    // ---------------------------------------------------------------- delete

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id, HttpServletRequest http) {

        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }

        Long partnerMid = partnerMerchantId(http);
        if (partnerMid != null) {
            Long owner = product.getMerchant() != null ? product.getMerchant().getId() : null;
            if (!partnerMid.equals(owner)) {
                return ResponseEntity.status(403).body(Map.of("error", "Ce produit n'appartient pas à votre commerçant."));
            }
        }

        productRepository.deleteById(id);

        auditLogService.log("DELETE_PRODUCT", "PRODUCT", "Product", id,
                "ADMIN_DEMO", "SUCCESS", "Product deleted");

        return ResponseEntity.noContent().build();
    }
}
