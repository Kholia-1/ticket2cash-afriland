package com.afriland.ticket2cash.merchant;

import com.afriland.ticket2cash.claim.Claim;
import com.afriland.ticket2cash.claim.ClaimRepository;
import com.afriland.ticket2cash.product.CashbackType;
import com.afriland.ticket2cash.product.Product;
import com.afriland.ticket2cash.product.ProductRepository;
import com.afriland.ticket2cash.product.ProductRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Partner-scoped data. Everything here is limited, ON THE SERVER, to the
 * logged-in partner's own merchant and its sub-merchants.
 *
 * A partner must never see another partner's claims, merchants or products.
 */
@RestController
@RequestMapping("/api/partner")
public class PartnerDataController {

    private final MerchantRepository merchantRepository;
    private final ClaimRepository claimRepository;
    private final ProductRepository productRepository;

    public PartnerDataController(MerchantRepository merchantRepository,
                                 ClaimRepository claimRepository,
                                 ProductRepository productRepository) {
        this.merchantRepository = merchantRepository;
        this.claimRepository = claimRepository;
        this.productRepository = productRepository;
    }

    private Long myMerchantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object mid = session.getAttribute("AUTH_MERCHANT_ID");
        if (mid == null) return null;
        try {
            return Long.valueOf(String.valueOf(mid));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** My merchant id + the ids of my sub-merchants. */
    private Set<Long> myScope(Long me) {
        Set<Long> ids = new HashSet<>();
        if (me == null) return ids;
        ids.add(me);
        for (Merchant sub : merchantRepository.findByParentMerchantId(me)) {
            ids.add(sub.getId());
        }
        return ids;
    }

    /** My merchant + my sub-merchants (used by the partner UI, never all partners). */
    @GetMapping("/merchants")
    public ResponseEntity<?> myMerchants(HttpServletRequest request) {
        Long me = myMerchantId(request);
        if (me == null) return ResponseEntity.status(403).body("No merchant linked to this account");
        List<Merchant> out = new ArrayList<>();
        merchantRepository.findById(me).ifPresent(out::add);
        out.addAll(merchantRepository.findByParentMerchantId(me));
        return ResponseEntity.ok(out);
    }

    /** Only claims for purchases made at my shop (or one of my sub-partners). */
    @GetMapping("/claims")
    public ResponseEntity<?> myClaims(HttpServletRequest request) {
        Long me = myMerchantId(request);
        if (me == null) return ResponseEntity.status(403).body("No merchant linked to this account");
        Set<Long> scope = myScope(me);
        List<Claim> out = new ArrayList<>();
        for (Long id : scope) out.addAll(claimRepository.findByMerchantId(id));
        return ResponseEntity.ok(out);
    }

    /** Only my products (and my sub-partners'). */
    @GetMapping("/products")
    public ResponseEntity<?> myProducts(HttpServletRequest request) {
        Long me = myMerchantId(request);
        if (me == null) return ResponseEntity.status(403).body("No merchant linked to this account");
        List<Product> out = new ArrayList<>();
        for (Long id : myScope(me)) out.addAll(productRepository.findByMerchantId(id));
        return ResponseEntity.ok(out);
    }

    @PostMapping("/products")
    public ResponseEntity<?> createProduct(@RequestBody ProductRequest request, HttpServletRequest httpRequest) {
        Long me = myMerchantId(httpRequest);
        if (me == null) return ResponseEntity.status(403).body("No merchant linked to this account");

        Long target = request.getMerchantId() != null ? request.getMerchantId() : me;
        if (!myScope(me).contains(target)) {
            return ResponseEntity.status(403).body("This merchant is not yours");
        }
        Merchant merchant = merchantRepository.findById(target).orElse(null);
        if (merchant == null) return ResponseEntity.badRequest().body("Merchant not found");

        Product product = new Product();
        apply(product, request, merchant);
        return ResponseEntity.ok(productRepository.save(product));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id,
                                           @RequestBody ProductRequest request,
                                           HttpServletRequest httpRequest) {
        Long me = myMerchantId(httpRequest);
        if (me == null) return ResponseEntity.status(403).body("No merchant linked to this account");

        Product product = productRepository.findById(id).orElse(null);
        if (product == null) return ResponseEntity.status(404).body("Product not found");
        if (!ownsProduct(product, myScope(me))) {
            return ResponseEntity.status(403).body("This product is not yours");
        }

        Merchant merchant = product.getMerchant();
        if (request.getMerchantId() != null && myScope(me).contains(request.getMerchantId())) {
            merchant = merchantRepository.findById(request.getMerchantId()).orElse(merchant);
        }
        apply(product, request, merchant);
        return ResponseEntity.ok(productRepository.save(product));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long me = myMerchantId(httpRequest);
        if (me == null) return ResponseEntity.status(403).body("No merchant linked to this account");

        Product product = productRepository.findById(id).orElse(null);
        if (product == null) return ResponseEntity.status(404).body("Product not found");
        if (!ownsProduct(product, myScope(me))) {
            return ResponseEntity.status(403).body("This product is not yours");
        }
        productRepository.delete(product);
        return ResponseEntity.ok(Map.of("message", "deleted"));
    }

    private boolean ownsProduct(Product product, Set<Long> scope) {
        return product.getMerchant() != null && scope.contains(product.getMerchant().getId());
    }

    private void apply(Product product, ProductRequest request, Merchant merchant) {
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
        product.setActive(request.getActive() != null ? request.getActive() : Boolean.TRUE);
    }
}
