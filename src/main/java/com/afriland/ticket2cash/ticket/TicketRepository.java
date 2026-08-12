package com.afriland.ticket2cash.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    boolean existsByTicketHash(String ticketHash);
}