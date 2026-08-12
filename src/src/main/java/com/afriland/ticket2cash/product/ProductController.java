package com.afriland.ticket2cash.product;

import com.afriland.ticket2cash.audit.AuditLogService;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

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

    @GetMapping
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @GetMapping("/merchant/{merchantId}")
    public List<Product> getProductsByMerchant(@PathVariable Long merchantId) {
        return productRepository.findByMerchantId(merchantId);
    }

    @PostMapping
    public ResponseEntity<?> createProduct(@RequestBody ProductRequest request) {

        Merchant merchant = merchantRepository.findById(request.getMerchantId()).orElse(null);

        if (merchant == null) {
            auditLogService.log(
                    "CREATE_PRODUCT_FAILED",
                    "PRODUCT",
                    "Product",
                    null,
                    "ADMIN_DEMO",
                    "FAILED",
                    "Merchant not found: " + request.getMerchantId()
            );

            return ResponseEntity.badRequest().body("Merchant not found with id = " + request.getMerchantId());
        }

        Product product = new Product();

        product.setMerchant(merchant);
        product.setSku(request.getSku());
        product.setName(request.getName());
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

        auditLogService.log(
                "CREATE_PRODUCT",
                "PRODUCT",
                "Product",
                savedProduct.getId(),
                "ADMIN_DEMO",
                "SUCCESS",
                "Product created: " + savedProduct.getName()
        );

        return ResponseEntity.ok(savedProduct);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody ProductRequest request) {

        Product product = productRepository.findById(id).orElse(null);

        if (product == null) {
            return ResponseEntity.notFound().build();
        }

        if (request.getMerchantId() != null) {
            Merchant merchant = merchantRepository.findById(request.getMerchantId()).orElse(null);
            if (merchant == null) {
                return ResponseEntity.badRequest().body("Merchant not found with id = " + request.getMerchantId());
            }
            product.setMerchant(merchant);
        }

        if (request.getSku() != null) product.setSku(request.getSku());
        if (request.getName() != null) product.setName(request.getName());
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

        auditLogService.log(
                "UPDATE_PRODUCT",
                "PRODUCT",
                "Product",
                saved.getId(),
                "ADMIN_DEMO",
                "SUCCESS",
                "Product updated: " + saved.getName()
        );

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {

        if (!productRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        productRepository.deleteById(id);

        auditLogService.log(
                "DELETE_PRODUCT",
                "PRODUCT",
                "Product",
                id,
                "ADMIN_DEMO",
                "SUCCESS",
                "Product deleted"
        );

        return ResponseEntity.noContent().build();
    }
}