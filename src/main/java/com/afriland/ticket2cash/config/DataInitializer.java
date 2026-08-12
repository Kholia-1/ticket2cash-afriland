package com.afriland.ticket2cash.config;

import com.afriland.ticket2cash.campaign.Campaign;
import com.afriland.ticket2cash.campaign.CampaignRepository;
import com.afriland.ticket2cash.campaign.CampaignStatus;
import com.afriland.ticket2cash.merchant.Merchant;
import com.afriland.ticket2cash.merchant.MerchantRepository;
import com.afriland.ticket2cash.merchant.MerchantStatus;
import com.afriland.ticket2cash.product.CashbackType;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class DataInitializer implements CommandLineRunner {

    private final MerchantRepository merchantRepository;
    private final CampaignRepository campaignRepository;

    public DataInitializer(MerchantRepository merchantRepository,
                           CampaignRepository campaignRepository) {
        this.merchantRepository = merchantRepository;
        this.campaignRepository = campaignRepository;
    }

    @Override
    public void run(String... args) {

        Merchant merchant;

        if (merchantRepository.count() == 0) {
            Merchant santaLucia = new Merchant();

            santaLucia.setName("Santa Lucia Yaounde");
            santaLucia.setBrandName("Santa Lucia");
            santaLucia.setRccm("RC/YAO/2026/B/001");
            santaLucia.setNiu("M012345678901A");
            santaLucia.setPhone("+237699000000");
            santaLucia.setEmail("contact@santalucia.cm");
            santaLucia.setCity("Yaounde");
            santaLucia.setAddress("Centre-ville");
            santaLucia.setStatus(MerchantStatus.ACTIVE);

            merchant = merchantRepository.save(santaLucia);
        } else {
            merchant = merchantRepository.findAll().get(0);
        }

        if (campaignRepository.count() == 0) {
            Campaign campaign = new Campaign();

            campaign.setMerchant(merchant);
            campaign.setName("5% cashback produits alimentaires");
            campaign.setDescription("Campagne cashback sur les produits alimentaires selectionnes");
            campaign.setStartDate(LocalDate.of(2026, 6, 1));
            campaign.setEndDate(LocalDate.of(2026, 12, 31));
            campaign.setCashbackType(CashbackType.PERCENTAGE);
            campaign.setCashbackValue(new BigDecimal("5"));
            campaign.setDailyLimitPerClient(new BigDecimal("5000"));
            campaign.setMonthlyLimitPerClient(new BigDecimal("50000"));
            campaign.setTotalBudget(new BigDecimal("2000000"));
            campaign.setStatus(CampaignStatus.ACTIVE);

            campaignRepository.save(campaign);
        }
    }
}