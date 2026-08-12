package com.afriland.ticket2cash.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    boolean existsByTicketHash(String ticketHash);

    List<Ticket> findByMerchantId(Long merchantId);
}
