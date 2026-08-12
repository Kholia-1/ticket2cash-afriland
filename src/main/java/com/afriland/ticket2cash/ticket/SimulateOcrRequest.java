package com.afriland.ticket2cash.ticket;

public class SimulateOcrRequest {

    private Long merchantId;
    private Long campaignId;
    private String userId;
    private String ticketNumber;

    public Long getMerchantId() { return merchantId; }

    public Long getCampaignId() { return campaignId; }

    public String getUserId() { return userId; }

    public String getTicketNumber() { return ticketNumber; }
}