package com.afriland.ticket2cash.request;

/**
 * Type of message a partner is sending to admin.
 * Drives the icon and default subject in the UI, and allows filtering the inbox.
 */
public enum PartnerRequestType {

    /** Partner wants a new cashback campaign to be created for them. */
    CAMPAIGN_REQUEST,

    /** Partner is asking a question about the platform. */
    QUESTION,

    /** Partner has an issue that needs technical or operational help. */
    SUPPORT,

    /** Any other type of message. */
    OTHER
}
