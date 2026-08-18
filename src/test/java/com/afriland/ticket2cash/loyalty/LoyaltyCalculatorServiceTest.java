package com.afriland.ticket2cash.loyalty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoyaltyCalculatorServiceTest {
    @Mock LoyaltyBatchRepository batches;
    @Mock LoyaltyRuleRepository rules;
    @Mock LoyaltyTransactionRepository transactions;
    @Mock LoyaltyResultRepository results;
    @Mock LoyaltyClientRepository clients;
    @Mock LoyaltyTierRepository tiers;

    @Test
    void calculatesImportedVolumeAndClientAggregates() {
        LoyaltyBatch batch = new LoyaltyBatch(); batch.setId(7L); batch.setName("test"); batch.setStatus(LoyaltyBatchStatus.IMPORTED);
        LoyaltyRule rule = new LoyaltyRule(); rule.setId(1L); rule.setName("Test"); rule.setActive(true); rule.setType(LoyaltyRuleType.FLAT_PERCENTAGE); rule.setPercentage(BigDecimal.ZERO);
        List<LoyaltyTransaction> rows = new ArrayList<>();
        add(rows,"CUST-LOY-ESS-001","Client Essentiel Test",100000);
        add(rows,"CUST-LOY-PREM-001","Client Premium Test",250000); add(rows,"CUST-LOY-PREM-001","Client Premium Test",250000);
        add(rows,"CUST-LOY-PREST-001","Client Prestige Test",750000); add(rows,"CUST-LOY-PREST-001","Client Prestige Test",800000);
        add(rows,"CUST-LOY-ELITE-001","Client Elite Test",1500000); add(rows,"CUST-LOY-ELITE-001","Client Elite Test",1600000);
        Map<String,LoyaltyClient> byAccount = new HashMap<>();
        rows.forEach(r -> byAccount.computeIfAbsent(r.getAccountNumber(), a -> { LoyaltyClient c=new LoyaltyClient(); c.setAccountNumber(a); c.setFullName(r.getClientName()); return c; }));
        when(batches.findById(7L)).thenReturn(Optional.of(batch)); when(batches.save(any())).thenAnswer(i -> i.getArgument(0));
        when(rules.findById(1L)).thenReturn(Optional.of(rule)); when(transactions.findByBatchId(7L)).thenReturn(rows);
        when(transactions.findByAccountNumber(any())).thenAnswer(i -> rows.stream().filter(r -> r.getAccountNumber().equals(i.getArgument(0))).toList());
        when(clients.findByAccountNumber(any())).thenAnswer(i -> Optional.of(byAccount.get(i.getArgument(0)))); when(clients.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tiers.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());

        LoyaltyBatch calculated = new LoyaltyCalculatorService(batches,rules,transactions,results,clients,null,tiers).calculate(7L,1L);

        assertEquals(new BigDecimal("5250000"), calculated.getTotalVolume());
        assertEquals(4, calculated.getClientCount());
        assertEquals(new BigDecimal("500000"), byAccount.get("CUST-LOY-PREM-001").getLifetimeVolume());
        assertEquals(new BigDecimal("1550000"), byAccount.get("CUST-LOY-PREST-001").getLifetimeVolume());
        assertEquals(new BigDecimal("3100000"), byAccount.get("CUST-LOY-ELITE-001").getLifetimeVolume());

        LoyaltyBatch second = new LoyaltyCalculatorService(batches,rules,transactions,results,clients,null,tiers).calculate(7L,1L);
        assertEquals(new BigDecimal("5250000"), second.getTotalVolume());
        assertEquals(new BigDecimal("500000"), byAccount.get("CUST-LOY-PREM-001").getLifetimeVolume());
        verify(transactions, times(1)).findByBatchId(7L);
    }

    private void add(List<LoyaltyTransaction> rows,String account,String name,int amount){
        LoyaltyTransaction tx=new LoyaltyTransaction();tx.setAccountNumber(account);tx.setClientName(name);tx.setAmount(BigDecimal.valueOf(amount));tx.setTransactionDate(LocalDate.of(2026,8,18));rows.add(tx);
    }
}
