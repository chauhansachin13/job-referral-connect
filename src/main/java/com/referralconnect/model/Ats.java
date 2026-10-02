package com.referralconnect.model;

/**
 * Where a company publishes its jobs: an applicant-tracking system with a public job-board API,
 * or (for the few giants that run their own) the company's own careers site.
 *
 * <p>How a board's {@code token} is written depends on the platform:
 * <ul>
 *   <li>Greenhouse, Lever, Ashby, SmartRecruiters: the company's board name, e.g. {@code stripe}</li>
 *   <li>Workday: {@code tenant/wdN/site}, e.g. {@code nvidia/wd5/NVIDIAExternalCareerSite}</li>
 *   <li>Eightfold: {@code host|domain}, e.g. {@code apply.careers.microsoft.com|microsoft.com}</li>
 *   <li>Oracle Recruiting Cloud: {@code host|siteNumber}, e.g. {@code jpmc.fa.oraclecloud.com|CX_1001}</li>
 *   <li>Amazon, Apple, Google: their own site; the token is just the company name</li>
 * </ul>
 */
public enum Ats {
    GREENHOUSE("Greenhouse"),
    LEVER("Lever"),
    ASHBY("Ashby"),
    WORKDAY("Workday"),
    SMARTRECRUITERS("SmartRecruiters"),
    EIGHTFOLD("Eightfold"),
    ORACLE("Oracle Recruiting"),
    AMAZON("amazon.jobs"),
    APPLE("jobs.apple.com"),
    GOOGLE("Google Careers");

    private final String label;

    Ats(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Board names on these platforms are case-insensitive, so they are stored lower-case. */
    public boolean caseInsensitiveToken() {
        return this == GREENHOUSE || this == LEVER || this == ASHBY;
    }

    @Override
    public String toString() {
        return label;
    }
}
