package com.referralconnect.model;

import com.referralconnect.json.Json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A seeker's request for a referral to one job, routed to one referrer at that company.
 * Carries frozen copies of the job and of the candidate's details: this is the "referral packet"
 * the referrer reads.
 */
public final class ReferralRequest {

    public record TimelineEntry(Instant at, String text) {
    }

    private final String id;
    private final Instant createdAt;
    private final String seekerId;
    private final String referrerId;
    private final String referrerTitle;
    private final JobPosting job;
    private CandidateProfile candidate;
    private String pitch;
    private RequestStatus status = RequestStatus.PENDING;
    private String referrerNote = "";
    private Instant updatedAt;
    private Instant viewedAt;
    private final List<TimelineEntry> timeline = new ArrayList<>();

    public ReferralRequest(String id, Instant createdAt, String seekerId, String referrerId, String referrerTitle,
                           JobPosting job, CandidateProfile candidate, String pitch) {
        this.id = id;
        this.createdAt = createdAt;
        this.seekerId = seekerId;
        this.referrerId = referrerId;
        this.referrerTitle = referrerTitle;
        this.job = job;
        this.candidate = candidate;
        this.pitch = pitch == null ? "" : pitch.trim();
        this.updatedAt = createdAt;
    }

    public String id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String seekerId() {
        return seekerId;
    }

    public String referrerId() {
        return referrerId;
    }

    public String referrerTitle() {
        return referrerTitle;
    }

    public JobPosting job() {
        return job;
    }

    public CandidateProfile candidate() {
        return candidate;
    }

    public String pitch() {
        return pitch;
    }

    public RequestStatus status() {
        return status;
    }

    public String referrerNote() {
        return referrerNote;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant viewedAt() {
        return viewedAt;
    }

    public List<TimelineEntry> timeline() {
        return Collections.unmodifiableList(timeline);
    }

    public void markViewed(Instant at) {
        if (viewedAt == null) {
            viewedAt = at;
            timeline.add(new TimelineEntry(at, "Referrer opened the request"));
        }
    }

    public void changeStatus(RequestStatus next, String note, Instant at, String event) {
        this.status = next;
        if (note != null && !note.isBlank()) {
            this.referrerNote = note.trim();
        }
        this.updatedAt = at;
        timeline.add(new TimelineEntry(at, event));
    }

    public void resubmit(CandidateProfile updated, String newPitch, Instant at) {
        this.candidate = updated;
        this.pitch = newPitch == null ? "" : newPitch.trim();
        this.status = RequestStatus.PENDING;
        this.updatedAt = at;
        timeline.add(new TimelineEntry(at, "Candidate updated details and resubmitted"));
    }

    public void addEvent(Instant at, String text) {
        timeline.add(new TimelineEntry(at, text));
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("createdAt", createdAt.toString());
        m.put("seekerId", seekerId);
        m.put("referrerId", referrerId);
        m.put("referrerTitle", referrerTitle);
        m.put("job", job.toJson());
        m.put("candidate", candidate.toJson());
        m.put("pitch", pitch);
        m.put("status", status.name());
        m.put("referrerNote", referrerNote);
        m.put("updatedAt", updatedAt.toString());
        m.put("viewedAt", viewedAt == null ? null : viewedAt.toString());
        List<Object> events = new ArrayList<>();
        for (TimelineEntry e : timeline) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("at", e.at().toString());
            em.put("text", e.text());
            events.add(em);
        }
        m.put("timeline", events);
        return m;
    }

    public static ReferralRequest fromJson(Map<String, Object> m) {
        ReferralRequest r = new ReferralRequest(
                Json.str(m, "id"),
                Instant.parse(Json.str(m, "createdAt")),
                Json.str(m, "seekerId"),
                Json.str(m, "referrerId"),
                Json.str(m, "referrerTitle"),
                JobPosting.fromJson(Json.obj(m, "job")),
                CandidateProfile.fromJson(Json.obj(m, "candidate")),
                Json.str(m, "pitch"));
        r.status = RequestStatus.valueOf(Json.str(m, "status"));
        r.referrerNote = Json.str(m, "referrerNote");
        r.updatedAt = Instant.parse(Json.str(m, "updatedAt"));
        Object viewed = m.get("viewedAt");
        r.viewedAt = viewed == null ? null : Instant.parse(viewed.toString());
        for (Object o : Json.arr(m, "timeline")) {
            Map<String, Object> em = Json.asObject(o);
            r.timeline.add(new TimelineEntry(Instant.parse(Json.str(em, "at")), Json.str(em, "text")));
        }
        return r;
    }
}
