package com.afriland.ticket2cash.cashback.engine;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CashbackTransactionRequest {
    private String transactionRef;
    private String cardHash;
    private String maskedCard;
    private String cardBin;
    private Long merchantId;
    private String merchantName;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime transactionDateTime;
    private String mccCode;
    private String channel;
    private String source;

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public String getCardHash() { return cardHash; }
    public void setCardHash(String cardHash) { this.cardHash = cardHash; }
    public String getMaskedCard() { return maskedCard; }
    public void setMaskedCard(String maskedCard) { this.maskedCard = maskedCard; }
    public String getCardBin() { return cardBin; }
    public void setCardBin(String cardBin) { this.cardBin = cardBin; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDateTime getTransactionDateTime() { return transactionDateTime; }
    public void setTransactionDateTime(LocalDateTime transactionDateTime) { this.transactionDateTime = transactionDateTime; }
    public String getMccCode() { return mccCode; }
    public void setMccCode(String mccCode) { this.mccCode = mccCode; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
