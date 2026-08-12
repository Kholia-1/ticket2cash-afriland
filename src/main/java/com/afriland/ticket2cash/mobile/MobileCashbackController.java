package com.afriland.ticket2cash.mobile;

import com.afriland.ticket2cash.campaign.Campaign;
import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.claim.Claim;
import com.afriland.ticket2cash.claim.ClaimRepository;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import com.afriland.ticket2cash.ticket.Ticket;
import com.afriland.ticket2cash.ticket.TicketRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mobile")
@CrossOrigin(origins = "*")
public class MobileCashbackController {

    private final ClaimRepository claimRepository;
    private final MerchantRepository merchantRepository;
    private final CampaignRepository campaignRepository;
    private final TicketRepository ticketRepository;
    private final MobileClientRepository clientRepository;

    public MobileCashbackController(ClaimRepository claimRepository,
                                     MerchantRepository merchantRepository,
                                     CampaignRepository campaignRepository,
                                     TicketRepository ticketRepository,
                                     MobileClientRepository clientRepository) {
        this.claimRepository = claimRepository;
        this.merchantRepository = merchantRepository;
        this.campaignRepository = campaignRepository;
        this.ticketRepository = ticketRepository;
        this.clientRepository = clientRepository;
    }

    @GetMapping("/cashbacks")
    public ResponseEntity<?> listCashbacks(HttpServletRequest request) {
        Long clientId = MobileAuthController.getMobileClientId(request);
        if (clientId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "NOT_AUTHENTICATED"));
        }

        var clientOpt = clientRepository.findById(clientId);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "CLIENT_NOT_FOUND"));
        }

        String phone = clientOpt.get().getPhone();

        List<Claim> claims = claimRepository.findAll().stream()
            .filter(c -> phone.equals(c.getUserId()))
            .sorted(Comparator.comparing(Claim::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());

        List<Map<String, Object>> result = claims.stream()
            .map(c -> claimToMap(c))
            .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/cashbacks/{id}")
    public ResponseEntity<?> getCashback(@PathVariable Long id, HttpServletRequest request) {
        Long clientId = MobileAuthController.getMobileClientId(request);
        if (clientId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "NOT_AUTHENTICATED"));
        }

        var clientOpt = clientRepository.findById(clientId);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "CLIENT_NOT_FOUND"));
        }

        String phone = clientOpt.get().getPhone();

        var claimOpt = claimRepository.findById(id);
        if (claimOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "CLAIM_NOT_FOUND"));
        }

        Claim claim = claimOpt.get();
        if (!phone.equals(claim.getUserId())) {
            return ResponseEntity.status(403).body(Map.of("error", "ACCESS_DENIED"));
        }

        return ResponseEntity.ok(claimToMap(claim));
    }

    @GetMapping("/campaigns")
    public ResponseEntity<?> listActiveCampaigns(HttpServletRequest request) {
        Long clientId = MobileAuthController.getMobileClientId(request);
        if (clientId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "NOT_AUTHENTICATED"));
        }

        List<Campaign> active = campaignRepository.findByStatus(
            com.afriland.ticket2cash.campaign.CampaignStatus.ACTIVE);

        List<Map<String, Object>> result = active.stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("name", c.getName());
            map.put("description", c.getDescription());
            map.put("cashbackType", c.getCashbackType() != null ? c.getCashbackType().name() : null);
            map.put("cashbackValue", c.getCashbackValue());
            map.put("startDate", c.getStartDate() != null ? c.getStartDate().toString() : null);
            map.put("endDate", c.getEndDate() != null ? c.getEndDate().toString() : null);
            if (c.getMerchant() != null) {
                map.put("merchantName", c.getMerchant().getName());
                map.put("merchantBrand", c.getMerchant().getBrandName());
            }
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/loyalty")
    public ResponseEntity<?> getLoyalty(HttpServletRequest request) {
        Long clientId = MobileAuthController.getMobileClientId(request);
        if (clientId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "NOT_AUTHENTICATED"));
        }

        var clientOpt = clientRepository.findById(clientId);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "CLIENT_NOT_FOUND"));
        }

        MobileClient client = clientOpt.get();
        Map<String, Object> loyalty = new LinkedHashMap<>();
        loyalty.put("tier", client.getTier());
        loyalty.put("tierPoints", client.getTierPoints());

        int nextTierTarget;
        String nextTier;
        switch (client.getTier()) {
            case "Bronze":  nextTier = "Argent";  nextTierTarget = 2000;  break;
            case "Argent":  nextTier = "Or";      nextTierTarget = 5000;  break;
            case "Or":      nextTier = "Platine"; nextTierTarget = 10000; break;
            default:        nextTier = "Platine"; nextTierTarget = 10000; break;
        }

        loyalty.put("nextTier", nextTier);
        loyalty.put("nextTierTarget", nextTierTarget);
        loyalty.put("progress", Math.min(100, (client.getTierPoints() * 100) / nextTierTarget));

        return ResponseEntity.ok(loyalty);
    }

    private Map<String, Object> claimToMap(Claim c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("claimReference", c.getClaimReference());
        map.put("status", c.getStatus() != null ? c.getStatus().name() : null);
        map.put("ticketAmount", c.getTicketAmount());
        map.put("cashbackAmount", c.getCashbackAmount());
        map.put("submittedAt", c.getSubmittedAt() != null ? c.getSubmittedAt().toString() : null);

        // Resolve merchant name
        if (c.getMerchantId() != null) {
            merchantRepository.findById(c.getMerchantId()).ifPresent(m -> {
                map.put("merchantName", m.getName());
                map.put("merchantBrand", m.getBrandName());
            });
        }

        // Resolve campaign name
        if (c.getCampaignId() != null) {
            campaignRepository.findById(c.getCampaignId()).ifPresent(camp -> {
                map.put("campaignName", camp.getName());
                if (camp.getCashbackValue() != null) {
                    map.put("cashbackRate", camp.getCashbackValue().intValue());
                }
            });
        }

        // Resolve ticket info
        if (c.getTicketId() != null) {
            ticketRepository.findById(c.getTicketId()).ifPresent(t -> {
                map.put("ticketNumber", t.getTicketNumber());
                map.put("ocrText", t.getOcrRawText());
            });
        }

        return map;
    }
}
