package com.referralconnect.model;

public enum Role {
    SEEKER("Job seeker"),
    REFERRER("Referrer");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
