package com.afriland.ticket2cash.loyalty;

/**
 * Type of loyalty cashback rule.
 *
 * <ul>
 *   <li>FLAT_PERCENTAGE — a fixed percentage of the qualifying transaction
 *       volume, applied to every client uniformly.</li>
 *   <li>TIERED_VOLUME — cashback percentage varies by the client's total
 *       qualifying volume during the period. Defined by a JSON list of
 *       {minVolume, percentage} entries in {@link LoyaltyRule#tiersJson}.</li>
 *   <li>CATEGORY_BASED — only transactions whose {@code category} matches the
 *       rule's {@code categoryFilter} qualify. A flat percentage is then
 *       applied.</li>
 * </ul>
 */
public enum LoyaltyRuleType {
    FLAT_PERCENTAGE,
    TIERED_VOLUME,
    CATEGORY_BASED
}
