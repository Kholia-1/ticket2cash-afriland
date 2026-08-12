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

        List<Claim> claims = claimRepository.findByUserIdOrderBySubmittedAtDesc(phone);

        Map<Long, Merchant> merchants = merchantRepository.findAllById(claims.stream()
                .map(Claim::getMerchantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Merchant::getId, m -> m));
        Map<Long, Campaign> campaigns = campaignRepository.findAllById(claims.stream()
                .map(Claim::getCampaignId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Campaign::getId, c -> c));
        Map<Long, Ticket> tickets = ticketRepository.findAllById(claims.stream()
                .map(Claim::getTicketId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Ticket::getId, t -> t));

        List<Map<String, Object>> result = claims.stream()
            .map(c -> claimToMap(c, merchants, campaigns, tickets))
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
        Map<Long, Merchant> merchants = c.getMerchantId() == null ? Collections.emptyMap()
                : merchantRepository.findById(c.getMerchantId()).map(m -> Map.of(m.getId(), m)).orElse(Collections.emptyMap());
        Map<Long, Campaign> campaigns = c.getCampaignId() == null ? Collections.emptyMap()
                : campaignRepository.findById(c.getCampaignId()).map(camp -> Map.of(camp.getId(), camp)).orElse(Collections.emptyMap());
        Map<Long, Ticket> tickets = c.getTicketId() == null ? Collections.emptyMap()
                : ticketRepository.findById(c.getTicketId()).map(t -> Map.of(t.getId(), t)).orElse(Collections.emptyMap());
        return claimToMap(c, merchants, campaigns, tickets);
    }

    private Map<String, Object> claimToMap(Claim c,
                                           Map<Long, Merchant> merchants,
                                           Map<Long, Campaign> campaigns,
                                           Map<Long, Ticket> tickets) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("claimReference", c.getClaimReference());
        map.put("status", c.getStatus() != null ? c.getStatus().name() : null);
        map.put("ticketAmount", c.getTicketAmount());
        map.put("cashbackAmount", c.getCashbackAmount());
        map.put("submittedAt", c.getSubmittedAt() != null ? c.getSubmittedAt().toString() : null);

        // Resolve merchant name
        if (c.getMerchantId() != null) {
            Optional.ofNullable(merchants.get(c.getMerchantId())).ifPresent(m -> {
                map.put("merchantName", m.getName());
                map.put("merchantBrand", m.getBrandName());
            });
        }

        // Resolve campaign name
        if (c.getCampaignId() != null) {
            Optional.ofNullable(campaigns.get(c.getCampaignId())).ifPresent(camp -> {
                map.put("campaignName", camp.getName());
                if (camp.getCashbackValue() != null) {
                    map.put("cashbackRate", camp.getCashbackValue().intValue());
                }
            });
        }

        // Resolve ticket info
        if (c.getTicketId() != null) {
            Optional.ofNullable(tickets.get(c.getTicketId())).ifPresent(t -> {
                map.put("ticketNumber", t.getTicketNumber());
                map.put("ocrText", t.getOcrRawText());
            });
        }

        return map;
    }
}
