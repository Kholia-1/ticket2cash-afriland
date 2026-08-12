package com.afriland.ticket2cash.campaign;

/**
 * Who owns a campaign — i.e. who created it and who can edit it.
 *
 * <ul>
 *   <li>{@link #ADMIN} — created by Afriland admins. Can target any merchant.
 *       Typically used for bank-wide loyalty campaigns (fidélité Afriland).</li>
 *   <li>{@link #MERCHANT} — created by a partner merchant, targets only their own
 *       merchant. Enforced server-side: merchantId is forced from the session,
 *       never trusted from the request body.</li>
 * </ul>
 */
public enum CampaignOwnerType {
    ADMIN,
    MERCHANT
}
