package com.referralconnect.model;

import com.referralconnect.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A job a seeker saved, and how far their application has got. Keeps its own copy of the job so
 * it stays on the board after the posting drops out of the scan window.
 */
public final class TrackedJob {

    public enum Stage {
        SAVED("Saved"),
        APPLIED("Applied"),
        INTERVIEWING("Interviewing"),
        OFFER("Offer"),
        CLOSED("Not selected");

        private final String label;

        Stage(String label) {
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

    private final String accountId;
    private final JobPosting job;
    private final Instant savedAt;
    private Stage stage;
    private String note;
    private Instant updatedAt;

    public TrackedJob(String accountId, JobPosting job, Instant savedAt, Stage stage, String note, Instant updatedAt) {
        this.accountId = accountId;
        this.job = job;
        this.savedAt = savedAt;
        this.stage = stage;
        this.note = note == null ? "" : note.trim();
        this.updatedAt = updatedAt;
    }

    public String accountId() {
        return accountId;
    }

    public JobPosting job() {
        return job;
    }

    public Instant savedAt() {
        return savedAt;
    }

    public Stage stage() {
        return stage;
    }

    public String note() {
        return note;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void move(Stage next, Instant at) {
        this.stage = next;
        this.updatedAt = at;
    }

    public void setNote(String text, Instant at) {
        this.note = text == null ? "" : text.trim();
        this.updatedAt = at;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accountId", accountId);
        m.put("job", job.toJson());
        m.put("savedAt", savedAt.toString());
        m.put("stage", stage.name());
        m.put("note", note);
        m.put("updatedAt", updatedAt.toString());
        return m;
    }

    public static TrackedJob fromJson(Map<String, Object> m) {
        return new TrackedJob(
                Json.str(m, "accountId"),
                JobPosting.fromJson(Json.obj(m, "job")),
                Instant.parse(Json.str(m, "savedAt")),
                Stage.valueOf(Json.str(m, "stage")),
                Json.str(m, "note"),
                Instant.parse(Json.str(m, "updatedAt")));
    }
}
