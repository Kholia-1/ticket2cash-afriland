package com.afriland.ticket2cash.loyalty;

/**
 * Lifecycle of a loyalty cashback batch.
 *
 * <ul>
 *   <li>IMPORTED — file uploaded, transactions parsed and persisted, waiting for a rule to be applied.</li>
 *   <li>CALCULATING — engine is computing cashback per client (async job in progress).</li>
 *   <li>CALCULATED — computation finished, results ready for review.</li>
 *   <li>APPROVED — an ADMIN has approved the results; ready for crediting.</li>
 *   <li>CREDITED — cashback has been pushed to Core Banking (all results marked CREDITED).</li>
 *   <li>REJECTED — batch was cancelled before crediting; results not paid.</li>
 *   <li>FAILED — an error occurred during import or calculation.</li>
 * </ul>
 */
public enum LoyaltyBatchStatus {
    IMPORTED,
    CALCULATING,
    CALCULATED,
    APPROVED,
    CREDITED,
    REJECTED,
    FAILED
}
