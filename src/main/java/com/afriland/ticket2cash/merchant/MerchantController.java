package com.afriland.ticket2cash.merchant;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.auth.AppUserRepository;
import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.claim.ClaimRepository;
import com.afriland.ticket2cash.common.ValidationUtils;
import com.afriland.ticket2cash.product.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Merchant CRUD. All create/update operations validate business inputs
 * (name, brand name, email, phone) via {@link ValidationUtils} and reject
 * anything that isn't a plausible real-world value (e.g. name "1", "@").
 * Duplicate merchant names are also refused so admins don't create phantoms.
 */
@RestController
@RequestMapping("/api/merchants")
public class MerchantController {

    private final MerchantRepository merchantRepository;
    private final AuditLogService auditLogService;
    private final ClaimRepository claimRepository;
    private final CampaignRepository campaignRepository;
    private final ProductRepository productRepository;
    private final AppUserRepository userRepository;

    public MerchantController(MerchantRepository merchantRepository,
                              AuditLogService auditLogService,
                              ClaimRepository claimRepository,
                              CampaignRepository campaignRepository,
                              ProductRepository productRepository,
                              AppUserRepository userRepository) {
        this.merchantRepository = merchantRepository;
        this.auditLogService = auditLogService;
        this.claimRepository = claimRepository;
        this.campaignRepository = campaignRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // ---------------------------------------------------------------- helpers

    private ResponseEntity<?> validateMerchantPayload(Merchant m, boolean isCreate, Long updatingId) {
        String err = ValidationUtils.validateName(m.getName(), "nom du commerçant");
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "name"));

        err = ValidationUtils.validateOptionalName(m.getBrandName(), "nom commercial");
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "brandName"));

        err = ValidationUtils.validateEmail(m.getEmail());
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "email"));

        err = ValidationUtils.validatePhone(m.getPhone());
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "phone"));

        err = ValidationUtils.validateOptionalName(m.getCity(), "nom de ville");
        if (err != null) return ResponseEntity.badRequest().body(Map.of("error", err, "field", "city"));

        // Reject duplicates (case-insensitive comparison on trimmed name)
        String trimmed = m.getName().trim();
        List<Merchant> all = merchantRepository.findAll();
        for (Merchant other : all) {
            if (updatingId != null && updatingId.equals(other.getId())) continue;
            if (other.getName() != null && trimmed.equalsIgnoreCase(other.getName().trim())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Un commerçant nommé '" + trimmed + "' existe déjà",
                        "field", "name"));
            }
        }
        return null; // valid
    }

    // ---------------------------------------------------------------- read

    @GetMapping
    public List<Merchant> getAllMerchants() {
        return merchantRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Merchant> getMerchantById(@PathVariable Long id) {
        return merchantRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ---------------------------------------------------------------- create

    @PostMapping
    public ResponseEntity<?> createMerchant(@RequestBody Merchant merchant) {
        ResponseEntity<?> validationError = validateMerchantPayload(merchant, true, null);
        if (validationError != null) return validationError;

        // Trim on save
        merchant.setName(merchant.getName().trim());
        if (merchant.getBrandName() != null) merchant.setBrandName(merchant.getBrandName().trim());
        if (merchant.getEmail() != null) merchant.setEmail(merchant.getEmail().trim());
        if (merchant.getPhone() != null) merchant.setPhone(merchant.getPhone().trim());

        Merchant saved = merchantRepository.save(merchant);

        auditLogService.log(
                "CREATE_MERCHANT", "MERCHANT", "Merchant", saved.getId(),
                "ADMIN_DEMO", "SUCCESS",
                "Merchant created: " + (saved.getBrandName() != null ? saved.getBrandName() : saved.getName())
        );

        return ResponseEntity.ok(saved);
    }

    // ---------------------------------------------------------------- update (status only for now)

    @PutMapping("/{id}/status")
    public ResponseEntity<Merchant> updateMerchantStatus(
            @PathVariable Long id,
            @RequestParam MerchantStatus status
    ) {
        return merchantRepository.findById(id)
                .map(merchant -> {
                    merchant.setStatus(status);
                    Merchant updated = merchantRepository.save(merchant);
                    auditLogService.log(
                            "UPDATE_MERCHANT_STATUS", "MERCHANT", "Merchant",
                            updated.getId(), "ADMIN_DEMO", "SUCCESS",
                            "Merchant status changed to " + status
                    );
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Full merchant update (name, brand, contact, etc.) with validation. */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateMerchant(@PathVariable Long id, @RequestBody Merchant patch) {
        Merchant existing = merchantRepository.findById(id).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();

        // Only validate fields that are being changed
        Merchant merged = new Merchant();
        merged.setName(patch.getName() != null ? patch.getName() : existing.getName());
        merged.setBrandName(patch.getBrandName() != null ? patch.getBrandName() : existing.getBrandName());
        merged.setEmail(patch.getEmail() != null ? patch.getEmail() : existing.getEmail());
        merged.setPhone(patch.getPhone() != null ? patch.getPhone() : existing.getPhone());
        merged.setCity(patch.getCity() != null ? patch.getCity() : existing.getCity());

        ResponseEntity<?> validationError = validateMerchantPayload(merged, false, id);
        if (validationError != null) return validationError;

        if (patch.getName() != null) existing.setName(patch.getName().trim());
        if (patch.getBrandName() != null) existing.setBrandName(patch.getBrandName().trim());
        if (patch.getEmail() != null) existing.setEmail(patch.getEmail().trim());
        if (patch.getPhone() != null) existing.setPhone(patch.getPhone().trim());
        if (patch.getCity() != null) existing.setCity(patch.getCity().trim());
        if (patch.getAddress() != null) existing.setAddress(patch.getAddress().trim());
        if (patch.getRccm() != null) existing.setRccm(patch.getRccm().trim());
        if (patch.getNiu() != null) existing.setNiu(patch.getNiu().trim());

        Merchant saved = merchantRepository.save(existing);
        auditLogService.log("UPDATE_MERCHANT", "MERCHANT", "Merchant", saved.getId(),
                "ADMIN_DEMO", "SUCCESS", "Merchant updated: " + saved.getName());
        return ResponseEntity.ok(saved);
    }

    // ---------------------------------------------------------------- delete (cascade)

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMerchant(@PathVariable Long id) {
        if (!merchantRepository.existsById(id)) return ResponseEntity.notFound().build();

        Merchant merchant = merchantRepository.findById(id).orElse(null);
        String merchantName = merchant != null ? merchant.getName() : "ID=" + id;

        try {
            claimRepository.findByMerchantId(id).forEach(c -> claimRepository.delete(c));
            campaignRepository.findByMerchantId(id).forEach(c -> campaignRepository.delete(c));
            productRepository.findByMerchantId(id).forEach(p -> productRepository.delete(p));
            userRepository.findAll().stream()
                    .filter(u -> id.equals(u.getMerchantId()))
                    .forEach(u -> userRepository.delete(u));
            merchantRepository.deleteById(id);

            auditLogService.log(
                    "DELETE_MERCHANT_CASCADE", "MERCHANT", "Merchant", id,
                    "ADMIN", "SUCCESS",
                    "Merchant deleted with all related data: " + merchantName
            );

            return ResponseEntity.ok(Map.of(
                    "message", "Commerçant '" + merchantName + "' et toutes ses données supprimées avec succès"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Erreur lors de la suppression: " + e.getMessage()
            ));
        }
    }
}
