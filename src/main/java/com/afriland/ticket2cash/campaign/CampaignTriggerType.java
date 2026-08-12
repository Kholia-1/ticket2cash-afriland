package com.afriland.ticket2cash.campaign;

/**
 * The four possible "triggers" that cause a cashback to be computed for a client.
 * A campaign has exactly one triggerType. The trigger determines <b>which data
 * source</b> feeds the cashback engine and <b>which fields</b> of the campaign
 * are meaningful.
 *
 * <p>Design reference: {@code Architecture_Moteur_Campagnes.md} §2.
 */
public enum CampaignTriggerType {

    /**
     * <b>Cashback marchand.</b> Every bank-side transaction at the merchant
     * (Netflix subscription, Total fuel, etc.) triggers cashback.
     * Data source: admin uploads bank transactions OR POS webhook stream.
     * Example: "5% cashback à tout client Afriland ayant payé Netflix avec sa
     * carte du 1er au 30 juillet".
     */
    MERCHANT_TRANSACTION,

    /**
     * <b>Cashback produit.</b> Purchase of a specific product line triggers
     * cashback. Requires OCR to identify products on a receipt.
     * Data source: OCR ticket + product matching.
     * Example: "10% cashback sur le shampoing X vendu chez Santa Lucia".
     */
    PRODUCT_PURCHASE,

    /**
     * <b>Cashback volume.</b> Cumulative spend by a client at a merchant during
     * the campaign window unlocks cashback once a threshold is crossed.
     * Data source: aggregated bank transactions per client per merchant.
     * Example: "Clients ayant dépensé >100 000 FCFA chez Total = 3% cashback".
     */
    VOLUME_THRESHOLD,

    /**
     * <b>Cashback événement POS.</b> Each qualifying POS transaction (over a
     * per-tx amount threshold) immediately fires a fixed cashback.
     * Data source: real-time webhook {@code /api/webhook/transaction}.
     * Example: "Chaque achat >50 000 FCFA chez Santa Lucia = 500 FCFA cashback".
     */
    POS_WEBHOOK_EVENT
}
