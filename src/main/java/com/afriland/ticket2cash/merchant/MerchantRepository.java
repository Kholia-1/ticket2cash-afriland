package com.afriland.ticket2cash.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    List<Merchant> findByParentMerchantId(Long parentMerchantId);
}
