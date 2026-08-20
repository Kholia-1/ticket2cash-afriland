package com.afriland.ticket2cash.loyalty;

import com.afriland.ticket2cash.rewards.RewardBenefitType;
import com.afriland.ticket2cash.rewards.RewardCalculationContext;
import com.afriland.ticket2cash.rewards.RewardEngineService;
import com.afriland.ticket2cash.rewards.RewardSourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Applies a {@link LoyaltyRule} to all transactions in a batch and materializes
 * one {@link LoyaltyResult} per client.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Load the batch and its rule; refuse to run if rule is missing or the
 *       batch is not in IMPORTED / CALCULATED state (idempotent re-runs OK).</li>
 *   <li>Delete any previous results for this batch (allows re-calculation with
 *       a different rule during preview).</li>
 *   <li>Group the batch's transactions by accountNumber.</li>
 *   <li>For each group, determine per-transaction qualification
 *       (minTransactionAmount, categoryFilter) and total the qualifying volume.</li>
 *   <li>Apply the rule type (FLAT_PERCENTAGE / TIERED_VOLUME / CATEGORY_BASED)
 *       to compute cashback. Enforce maxCashbackPerClient cap.</li>
 *   <li>Persist one LoyaltyResult per client, update client aggregate
 *       (lifetimeCashback / lifetimeVolume are only touched at credit time,
 *        not at calculation time, so preview never mutates client totals).</li>
 *   <li>Update batch counters and status → CALCULATED.</li>
 * </ol>
 */
@Service
public class LoyaltyCalculatorService {

    private final LoyaltyBatchRepository batchRepository;
    private final LoyaltyRuleRepository ruleRepository;
    private final LoyaltyTransactionRepository transactionRepository;
    private final LoyaltyResultRepository resultRepository;
    private final LoyaltyClientRepository clientRepository;
    private final LoyaltyTierRepository tierRepository;
    private final RewardEngineService rewardEngineService;

    /** Kept for source compatibility with callers that construct this service directly. */
    public LoyaltyCalculatorService(LoyaltyBatchRepository batchRepository,
                                    LoyaltyRuleRepository ruleRepository,
                                    LoyaltyTransactionRepository transactionRepository,
                                    LoyaltyResultRepository resultRepository,
                                    LoyaltyClientRepository clientRepository) {
        this(batchRepository, ruleRepository, transactionRepository, resultRepository, clientRepository, null, null);
    }

    public LoyaltyCalculatorService(LoyaltyBatchRepository batchRepository,
                                    LoyaltyRuleRepository ruleRepository,
                                    LoyaltyTransactionRepository transactionRepository,
                                    LoyaltyResultRepository resultRepository,
                                    LoyaltyClientRepository clientRepository,
                                    RewardEngineService rewardEngineService) {
        this(batchRepository, ruleRepository, transactionRepository, resultRepository, clientRepository, rewardEngineService, null);
    }

    @Autowired
    public LoyaltyCalculatorService(LoyaltyBatchRepository batchRepository,
                                    LoyaltyRuleRepository ruleRepository,
                                    LoyaltyTransactionRepository transactionRepository,
                                    LoyaltyResultRepository resultRepository,
                                    LoyaltyClientRepository clientRepository,
                                    RewardEngineService rewardEngineService,
                                    LoyaltyTierRepository tierRepository) {
        this.batchRepository = batchRepository;
        this.ruleRepository = ruleRepository;
        this.transactionRepository = transactionRepository;
        this.resultRepository = resultRepository;
        this.clientRepository = clientRepository;
        this.rewardEngineService = rewardEngineService;
        this.tierRepository = tierRepository;
    }

    @Transactional
    public LoyaltyBatch calculate(Long batchId, Long ruleId) {
        LoyaltyBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        if (batch.getStatus() == LoyaltyBatchStatus.CALCULATED) {
            // A calculated batch is immutable from the calculation endpoint.
            // This prevents a second click from applying the same business
            // operation again and gives the caller an idempotent result.
            if (batch.getTotalVolume() == null || batch.getTotalVolume().signum() == 0) {
                repairMissingBatchVolume(batch);
            }
            return batch;
        }
        if (batch.getStatus() == LoyaltyBatchStatus.CREDITED
                || batch.getStatus() == LoyaltyBatchStatus.APPROVED) {
            throw new IllegalStateException("Cannot recalculate a batch that is " + batch.getStatus());
        }

        LoyaltyRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + ruleId));
        if (rule.getActive() == null || !rule.getActive()) {
            throw new IllegalStateException("Rule is inactive: " + rule.getName());
        }

        batch.setStatus(LoyaltyBatchStatus.CALCULATING);
        batch.setRuleId(rule.getId());
        batch.setRuleNameSnapshot(rule.getName());
        batchRepository.save(batch);

        // Wipe any previous results (idempotent recalc)
        resultRepository.deleteByBatchId(batchId);

        List<LoyaltyTransaction> txs = transactionRepository.findByBatchId(batchId);

        // The batch volume is the volume imported in this file, independently
        // of whether a loyalty rule later qualifies a row for cashback.
        // Keeping this separate from the eligible/cashback volume prevents the
        // import report from showing 0 when clients were updated but a rule
        // filtered out their cashback.
        BigDecimal importedBatchVolume = sumImportedVolume(txs);

        // Also reset per-tx qualified flag / cashback so a rerun with a different
        // rule gives clean data on the transaction rows.
        for (LoyaltyTransaction tx : txs) {
            tx.setQualified(false);
            tx.setCashbackAmount(BigDecimal.ZERO);
        }

        // Parse rule tier structure once
        List<TierEntry> tiers = parseTiers(rule.getTiersJson());

        // Group by client
        Map<String, List<LoyaltyTransaction>> byClient = new LinkedHashMap<>();
        for (LoyaltyTransaction tx : txs) {
            byClient.computeIfAbsent(tx.getAccountNumber(), k -> new ArrayList<>()).add(tx);
        }

        // Tier filter set
        Set<String> allowedTiers = parseTierFilter(rule.getTierFilter());

        // Entity type filter (banking-informed criteria, V4)
        String entityTypeFilter = rule.getEntityTypeFilter();
        boolean hasEntityFilter = entityTypeFilter != null && !entityTypeFilter.isBlank()
                                  && !"ALL".equalsIgnoreCase(entityTypeFilter);

        int minTxCount = rule.getMinTransactionCount() != null ? rule.getMinTransactionCount() : 0;
        BigDecimal minAvgValue = rule.getMinAvgTransactionValue();
        Integer minAgeMonths = rule.getMinAccountAgeMonths();
        java.time.LocalDate today = java.time.LocalDate.now();

        int qualifiedRowCount = 0;
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal totalCashback = BigDecimal.ZERO;
        int clientsWithCashback = 0;

        for (Map.Entry<String, List<LoyaltyTransaction>> e : byClient.entrySet()) {
            String account = e.getKey();
            List<LoyaltyTransaction> group = e.getValue();

            // Resolve or auto-create the client row
            LoyaltyClient client = clientRepository.findByAccountNumber(account)
                    .orElseGet(() -> autoCreateClient(account, group));

            // Skip clients not in tier filter
            if (allowedTiers != null && !allowedTiers.contains(safeUpper(client.getTier()))) {
                LoyaltyResult skipped = buildResult(batch.getId(), client, group.size(),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
                skipped.setNote("Excluded by tier filter (" + rule.getTierFilter() + ")");
                resultRepository.save(skipped);
                continue;
            }

            // Skip clients not matching entityType filter
            if (hasEntityFilter && !entityTypeFilter.equalsIgnoreCase(client.getEntityType())) {
                LoyaltyResult skipped = buildResult(batch.getId(), client, group.size(),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
                skipped.setNote("Excluded by segment filter (" + entityTypeFilter + ")");
                resultRepository.save(skipped);
                continue;
            }

            // Skip clients whose account is too young
            if (minAgeMonths != null && minAgeMonths > 0) {
                java.time.LocalDate opened = client.getAccountOpenedDate();
                if (opened == null) {
                    LoyaltyResult skipped = buildResult(batch.getId(), client, group.size(),
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
                    skipped.setNote("Excluded: accountOpenedDate missing (min age " + minAgeMonths + "mo)");
                    resultRepository.save(skipped);
                    continue;
                }
                long months = java.time.temporal.ChronoUnit.MONTHS.between(opened, today);
                if (months < minAgeMonths) {
                    LoyaltyResult skipped = buildResult(batch.getId(), client, group.size(),
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
                    skipped.setNote("Excluded: account only " + months + " months old (min " + minAgeMonths + ")");
                    resultRepository.save(skipped);
                    continue;
                }
            }

            // Compute qualifying volume for this client
            BigDecimal clientVolume = BigDecimal.ZERO;
            int clientQualified = 0;
            for (LoyaltyTransaction tx : group) {
                if (qualifies(tx, rule)) {
                    tx.setQualified(true);
                    // Only count positive spend (debit) amounts as volume.
                    // amount is signed: positive = credit, negative = debit.
                    // Loyalty cashback is on outgoing (debit) spend, so we use abs of negatives.
                    BigDecimal amt = tx.getAmount();
                    BigDecimal spend = amt.signum() < 0 ? amt.abs() : amt;
                    clientVolume = clientVolume.add(spend);
                    clientQualified++;
                }
            }

            // Enforce minPeriodVolume
            if (rule.getMinPeriodVolume() != null
                    && clientVolume.compareTo(rule.getMinPeriodVolume()) < 0) {
                LoyaltyResult skipped = buildResult(batch.getId(), client, group.size(),
                        clientVolume, BigDecimal.ZERO, BigDecimal.ZERO);
                skipped.setNote(String.format(Locale.ROOT,
                        "Volume %s below minPeriodVolume %s",
                        clientVolume.toPlainString(), rule.getMinPeriodVolume().toPlainString()));
                resultRepository.save(skipped);
                totalVolume = totalVolume.add(clientVolume);
                qualifiedRowCount += clientQualified;
                continue;
            }

            // Enforce minTransactionCount
            if (minTxCount > 0 && clientQualified < minTxCount) {
                LoyaltyResult skipped = buildResult(batch.getId(), client, group.size(),
                        clientVolume, BigDecimal.ZERO, BigDecimal.ZERO);
                skipped.setNote(String.format(Locale.ROOT,
                        "Only %d qualifying tx (min %d)", clientQualified, minTxCount));
                resultRepository.save(skipped);
                totalVolume = totalVolume.add(clientVolume);
                qualifiedRowCount += clientQualified;
                continue;
            }

            // Enforce minAvgTransactionValue
            if (minAvgValue != null && minAvgValue.signum() > 0 && clientQualified > 0) {
                BigDecimal avg = clientVolume.divide(BigDecimal.valueOf(clientQualified), 2, RoundingMode.HALF_UP);
                if (avg.compareTo(minAvgValue) < 0) {
                    LoyaltyResult skipped = buildResult(batch.getId(), client, group.size(),
                            clientVolume, BigDecimal.ZERO, BigDecimal.ZERO);
                    skipped.setNote(String.format(Locale.ROOT,
                            "Avg tx %s below minAvg %s",
                            avg.toPlainString(), minAvgValue.toPlainString()));
                    resultRepository.save(skipped);
                    totalVolume = totalVolume.add(clientVolume);
                    qualifiedRowCount += clientQualified;
                    continue;
                }
            }

            // Apply the rate
            BigDecimal rate = resolveRate(rule, clientVolume, tiers);
            BigDecimal cashback = clientVolume
                    .multiply(rate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            // Cap
            if (rule.getMaxCashbackPerClient() != null
                    && cashback.compareTo(rule.getMaxCashbackPerClient()) > 0) {
                cashback = rule.getMaxCashbackPerClient();
            }

            // Distribute cashback back to individual transaction rows pro rata
            // (for reporting only). Skip if volume == 0.
            if (clientVolume.signum() > 0 && cashback.signum() > 0) {
                for (LoyaltyTransaction tx : group) {
                    if (Boolean.TRUE.equals(tx.getQualified())) {
                        BigDecimal spend = tx.getAmount().signum() < 0
                                ? tx.getAmount().abs() : tx.getAmount();
                        BigDecimal share = spend
                                .multiply(cashback)
                                .divide(clientVolume, 2, RoundingMode.HALF_UP);
                        tx.setCashbackAmount(share);
                    }
                }
            }

            LoyaltyResult result = buildResult(batch.getId(), client, group.size(),
                    clientVolume, cashback, rate);
            resultRepository.save(result);

            // Progressive consolidation: retain the existing LoyaltyResult and
            // batch totals, while recording the same monetary benefit in the
            // common ledger. A ledger failure must not break the legacy batch.
            if (rewardEngineService != null && cashback.signum() > 0) {
                try {
                    RewardCalculationContext reward = new RewardCalculationContext();
                    reward.setSourceType(RewardSourceType.LOYALTY_BATCH);
                    String customerRef = safeCustomerReference(account);
                    reward.setSourceRef("BATCH-" + batch.getId() + "-CLIENT-"
                            + customerRef.substring(0, Math.min(16, customerRef.length())));
                    reward.setCustomerRef(customerRef);
                    reward.setAmount(clientVolume);
                    reward.setCurrency("FCFA");
                    reward.setTransactionDate(LocalDateTime.now());
                    reward.setBenefitType(RewardBenefitType.CASHBACK_FIXED);
                    reward.setBenefitValue(cashback);
                    rewardEngineService.calculate(reward);
                } catch (RuntimeException ignored) {
                    // Existing loyalty calculation remains authoritative during migration.
                }
            }

            totalVolume = totalVolume.add(clientVolume);
            totalCashback = totalCashback.add(cashback);
            qualifiedRowCount += clientQualified;
            if (cashback.signum() > 0) clientsWithCashback++;
        }

        // Persist tx updates in one shot
        transactionRepository.saveAll(txs);

        // Refresh cumulative client metrics from all persisted transactions. This
        // makes recalculation idempotent and keeps the client screen consistent
        // with the imported volume instead of leaving aggregates at zero.
        for (String account : byClient.keySet()) {
            refreshClientAggregates(account);
        }

        batch.setQualifiedRows(qualifiedRowCount);
        batch.setTotalVolume(importedBatchVolume);
        batch.setTotalCashback(totalCashback);
        batch.setClientCount(byClient.size());
        batch.setCalculatedAt(LocalDateTime.now());
        batch.setStatus(LoyaltyBatchStatus.CALCULATED);
        batch.setNote(String.format(Locale.ROOT,
                "Applied rule '%s': %d/%d clients earned cashback; total %s FCFA.",
                rule.getName(), clientsWithCashback, byClient.size(),
                totalCashback.toPlainString()));

        return batchRepository.save(batch);
    }

    private BigDecimal sumImportedVolume(List<LoyaltyTransaction> transactions) {
        BigDecimal total = BigDecimal.ZERO;
        for (LoyaltyTransaction tx : transactions) {
            if (tx == null || tx.getAmount() == null) continue;
            // Imported rows are validated as positive amounts.  abs also keeps
            // legacy signed exports compatible without touching client totals.
            total = total.add(tx.getAmount().abs());
        }
        return total;
    }

    /** Keep account/card-like identifiers out of the common ledger and audit details. */
    private String safeCustomerReference(String value) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toHexString(value.hashCode());
        }
    }

    // ---------- helpers ----------

    private LoyaltyClient autoCreateClient(String account, List<LoyaltyTransaction> group) {
        LoyaltyClient c = new LoyaltyClient();
        c.setAccountNumber(account);
        // Take the first non-null client name we see
        for (LoyaltyTransaction tx : group) {
            if (tx.getClientName() != null && !tx.getClientName().isBlank()) {
                c.setFullName(tx.getClientName());
                break;
            }
        }
        c.setTier("CLASSIC");
        return clientRepository.save(c);
    }

    private void refreshClientAggregates(String account) {
        if (account == null || account.isBlank()) return;
        LoyaltyClient client = clientRepository.findByAccountNumber(account).orElse(null);
        if (client == null) return;
        List<LoyaltyTransaction> all = transactionRepository.findByAccountNumber(account);
        BigDecimal volume = BigDecimal.ZERO;
        int count = 0;
        LocalDateTime last = null;
        for (LoyaltyTransaction tx : all) {
            if (tx.getAmount() == null) continue;
            BigDecimal spend = tx.getAmount().signum() < 0 ? tx.getAmount().abs() : tx.getAmount();
            if (spend.signum() <= 0) continue;
            volume = volume.add(spend);
            count++;
            if (last == null || (tx.getTransactionDate() != null && tx.getTransactionDate().atStartOfDay().isAfter(last))) {
                last = tx.getTransactionDate() == null ? last : tx.getTransactionDate().atStartOfDay();
            }
        }
        client.setLifetimeVolume(volume);
        client.setTransactionCount(count);
        if (last != null) client.setLastActivityAt(last);
        client.setTier(resolveTier(volume, count, client.getTier()));
        clientRepository.save(client);
    }

    /** Repairs legacy calculated batches persisted before totalVolume was populated. */
    private void repairMissingBatchVolume(LoyaltyBatch batch) {
        List<LoyaltyTransaction> rows = transactionRepository.findByBatchId(batch.getId());
        BigDecimal volume = BigDecimal.ZERO;
        Set<String> accounts = new HashSet<>();
        for (LoyaltyTransaction tx : rows) {
            if (tx.getAccountNumber() != null) accounts.add(tx.getAccountNumber());
            if (tx.getAmount() != null && tx.getAmount().signum() > 0) volume = volume.add(tx.getAmount());
            else if (tx.getAmount() != null && tx.getAmount().signum() < 0) volume = volume.add(tx.getAmount().abs());
        }
        if (volume.signum() > 0) {
            batch.setTotalVolume(volume);
            if (batch.getClientCount() == null || batch.getClientCount() == 0) batch.setClientCount(accounts.size());
            batchRepository.save(batch);
        }
    }

    private String resolveTier(BigDecimal volume, int transactionCount, String current) {
        if (tierRepository == null) return normalizeLegacyTier(current);
        String selected = "ESSENTIEL";
        for (LoyaltyTier tier : tierRepository.findByActiveTrueOrderBySortOrderAsc()) {
            BigDecimal minVolume = tier.getMinCumulativeSpend() == null ? BigDecimal.ZERO : tier.getMinCumulativeSpend();
            // Tier attribution is based on cumulative loyalty volume. Minimum
            // transaction and amount criteria belong to advantage-rule
            // eligibility and must not keep a high-volume client on Essentiel.
            if (volume.compareTo(minVolume) >= 0) selected = tier.getName();
        }
        return selected;
    }

    private String normalizeLegacyTier(String tier) {
        if (tier == null || tier.isBlank() || "CLASSIC".equalsIgnoreCase(tier)) return "Essentiel";
        return tier;
    }

    private boolean qualifies(LoyaltyTransaction tx, LoyaltyRule rule) {
        if (tx.getAmount() == null) return false;
        BigDecimal spend = tx.getAmount().signum() < 0 ? tx.getAmount().abs() : tx.getAmount();
        if (spend.signum() == 0) return false;
        if (rule.getMinTransactionAmount() != null
                && spend.compareTo(rule.getMinTransactionAmount()) < 0) return false;
        if (rule.getType() == LoyaltyRuleType.CATEGORY_BASED) {
            if (rule.getCategoryFilter() == null || rule.getCategoryFilter().isBlank()) return false;
            String want = rule.getCategoryFilter().trim().toUpperCase(Locale.ROOT);
            String have = tx.getCategory() == null ? "" : tx.getCategory().trim().toUpperCase(Locale.ROOT);
            if (!want.equals(have)) return false;
        }
        return true;
    }

    private BigDecimal resolveRate(LoyaltyRule rule, BigDecimal volume, List<TierEntry> tiers) {
        switch (rule.getType()) {
            case FLAT_PERCENTAGE:
            case CATEGORY_BASED:
                return rule.getPercentage() == null ? BigDecimal.ZERO : rule.getPercentage();
            case TIERED_VOLUME:
                if (tiers.isEmpty()) return BigDecimal.ZERO;
                BigDecimal chosen = BigDecimal.ZERO;
                for (TierEntry t : tiers) {
                    if (volume.compareTo(t.minVolume) >= 0) chosen = t.percentage;
                }
                return chosen;
            default:
                return BigDecimal.ZERO;
        }
    }

    private LoyaltyResult buildResult(Long batchId, LoyaltyClient client, int txCount,
                                      BigDecimal volume, BigDecimal cashback, BigDecimal rate) {
        LoyaltyResult r = new LoyaltyResult();
        r.setBatchId(batchId);
        r.setAccountNumber(client.getAccountNumber());
        r.setClientName(client.getFullName());
        r.setPhone(client.getPhone());
        r.setCardNumber(client.getCardNumber());
        r.setTier(client.getTier());
        r.setEntityType(client.getEntityType());
        r.setTransactionCount(txCount);
        r.setTotalVolume(volume);
        r.setCashbackAmount(cashback);
        r.setEffectiveRate(rate);
        r.setStatus(LoyaltyResultStatus.PENDING);
        return r;
    }

    private Set<String> parseTierFilter(String filter) {
        if (filter == null || filter.isBlank()) return null;
        Set<String> out = new HashSet<>();
        for (String s : filter.split(",")) {
            String v = s.trim().toUpperCase(Locale.ROOT);
            if (!v.isEmpty()) out.add(v);
        }
        return out.isEmpty() ? null : out;
    }

    private String safeUpper(String s) {
        return s == null ? "CLASSIC" : s.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Parse the JSON tier structure without pulling in a JSON library —
     * the format is well-defined and this keeps compile-time deps small.
     */
    List<TierEntry> parseTiers(String json) {
        List<TierEntry> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        // Extract each { ... } object
        int i = 0;
        while (i < json.length()) {
            int open = json.indexOf('{', i);
            if (open < 0) break;
            int close = json.indexOf('}', open);
            if (close < 0) break;
            String body = json.substring(open + 1, close);
            BigDecimal minVol = null, pct = null;
            for (String kv : body.split(",")) {
                String[] parts = kv.split(":");
                if (parts.length != 2) continue;
                String key = parts[0].trim().replace("\"", "").toLowerCase(Locale.ROOT);
                String val = parts[1].trim().replace("\"", "");
                try {
                    if (key.equals("minvolume")) minVol = new BigDecimal(val);
                    else if (key.equals("percentage") || key.equals("rate")) pct = new BigDecimal(val);
                } catch (NumberFormatException ignored) {}
            }
            if (minVol != null && pct != null) out.add(new TierEntry(minVol, pct));
            i = close + 1;
        }
        out.sort(Comparator.comparing(t -> t.minVolume));
        return out;
    }

    static class TierEntry {
        final BigDecimal minVolume;
        final BigDecimal percentage;
        TierEntry(BigDecimal minVolume, BigDecimal percentage) {
            this.minVolume = minVolume;
            this.percentage = percentage;
        }
    }
}
