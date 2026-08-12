package com.afriland.ticket2cash.request;

/**
 * Lifecycle status of a partner request.
 */
public enum PartnerRequestStatus {

    /** Just sent, admin has not yet looked at it. */
    OPEN,

    /** An admin has claimed it and is working on a response. */
    IN_PROGRESS,

    /** An admin has responded. Partner can view the response. */
    RESPONDED,

    /** Conversation closed — no further action expected. */
    CLOSED
}
