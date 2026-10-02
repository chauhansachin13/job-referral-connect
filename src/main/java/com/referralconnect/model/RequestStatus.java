package com.referralconnect.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a referral request.
 *
 * <pre>
 *   PENDING ──► REFERRED            (referrer submitted the referral)
 *      │  ╲───► DECLINED            (referrer can't refer)
 *      │   ╲──► NEEDS_INFO ──► PENDING  (seeker updated details and resubmitted)
 *      └──────► WITHDRAWN           (seeker cancelled; also allowed from NEEDS_INFO)
 * </pre>
 */
public enum RequestStatus {
    PENDING("Pending"),
    NEEDS_INFO("Needs more info"),
    REFERRED("Referred"),
    DECLINED("Declined"),
    WITHDRAWN("Withdrawn");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Still waiting on someone, i.e. counts against a referrer's workload. */
    public boolean isOpen() {
        return this == PENDING || this == NEEDS_INFO;
    }

    /** Statuses a referrer may move a request to from this one. */
    public Set<RequestStatus> referrerMoves() {
        return switch (this) {
            case PENDING -> EnumSet.of(REFERRED, NEEDS_INFO, DECLINED);
            case NEEDS_INFO -> EnumSet.of(REFERRED, DECLINED);
            default -> EnumSet.noneOf(RequestStatus.class);
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
