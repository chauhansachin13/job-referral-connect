package com.referralconnect.model;

/** Applicant-tracking systems whose public job-board APIs we can read without scraping. */
public enum Ats {
    GREENHOUSE("Greenhouse", "https://boards-api.greenhouse.io/v1/boards/%s/jobs", "https://job-boards.greenhouse.io/%s"),
    LEVER("Lever", "https://api.lever.co/v0/postings/%s?mode=json", "https://jobs.lever.co/%s"),
    ASHBY("Ashby", "https://api.ashbyhq.com/posting-api/job-board/%s", "https://jobs.ashbyhq.com/%s");

    private final String label;
    private final String apiPattern;
    private final String boardPattern;

    Ats(String label, String apiPattern, String boardPattern) {
        this.label = label;
        this.apiPattern = apiPattern;
        this.boardPattern = boardPattern;
    }

    public String label() {
        return label;
    }

    public String apiUrl(String token) {
        return apiPattern.formatted(token);
    }

    public String boardUrl(String token) {
        return boardPattern.formatted(token);
    }

    @Override
    public String toString() {
        return label;
    }
}
