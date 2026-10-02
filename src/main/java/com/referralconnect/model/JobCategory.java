package com.referralconnect.model;

/** The role families this system tracks. Anything else found on a job board is ignored. */
public enum JobCategory {
    SOFTWARE_DEVELOPER("Software Developer", "SDE"),
    DATA_ANALYST("Data Analyst", "Data Analyst"),
    DATA_SCIENTIST("Data Scientist / ML", "Data Sci / ML"),
    DATA_ENGINEER("Data Engineer", "Data Engineer"),
    FORWARD_DEPLOYED("FDE / Solutions", "FDE / Solutions");

    private final String label;
    private final String shortLabel;

    JobCategory(String label, String shortLabel) {
        this.label = label;
        this.shortLabel = shortLabel;
    }

    public String label() {
        return label;
    }

    /** Compact form for table cells. */
    public String shortLabel() {
        return shortLabel;
    }

    @Override
    public String toString() {
        return label;
    }
}
