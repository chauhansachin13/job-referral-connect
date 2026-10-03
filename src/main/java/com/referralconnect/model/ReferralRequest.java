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
 * the referrer reads. Both sides can also exchange messages on it.
 */
public final class ReferralRequest {

    /** Who caused a timeline event or wrote a message. */
    public enum Actor { SEEKER, REFERRER, SYSTEM }

    public record TimelineEntry(Instant at, String text, Actor actor) {
        public TimelineEntry(Instant at, String text) {
            this(at, text, guessActor(text));
        }

        /** Data saved before events recorded their actor: tell from the wording. */
        static Actor guessActor(String text) {
            return text.startsWith("Referr") ? Actor.REFERRER
                    : text.startsWith("Candidate") || text.startsWith("Request sent") ? Actor.SEEKER : Actor.SYSTEM;
        }
    }

    public record Message(Instant at, Actor from, String text) {
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
    private Instant lastReminderAt;
    private final List<TimelineEntry> timeline = new ArrayList<>();
    private final List<Message> messages = new ArrayList<>();

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

    /** "Priya Sharma" out of "Priya Sharma, SDE-2 at MongoDB". */
    public String referrerName() {
        int comma = referrerTitle.indexOf(',');
        int at = referrerTitle.indexOf(" at ");
        int cut = comma >= 0 ? comma : at >= 0 ? at : referrerTitle.length();
        return referrerTitle.substring(0, cut);
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

    public Instant lastReminderAt() {
        return lastReminderAt;
    }

    public List<TimelineEntry> timeline() {
        return Collections.unmodifiableList(timeline);
    }

    public List<Message> messages() {
        return Collections.unmodifiableList(messages);
    }

    /** When anything last happened on this request: a status change, an event or a message. */
    public Instant lastActivityAt() {
        Instant last = updatedAt;
        for (TimelineEntry e : timeline) {
            if (e.at().isAfter(last)) {
                last = e.at();
            }
        }
        for (Message m : messages) {
            if (m.at().isAfter(last)) {
                last = m.at();
            }
        }
        return last;
    }

    public void markViewed(Instant at) {
        if (viewedAt == null) {
            viewedAt = at;
            timeline.add(new TimelineEntry(at, "Referrer opened the request", Actor.REFERRER));
        }
    }

    public void changeStatus(RequestStatus next, String note, Instant at, String event, Actor actor) {
        this.status = next;
        if (note != null && !note.isBlank()) {
            this.referrerNote = note.trim();
        }
        this.updatedAt = at;
        timeline.add(new TimelineEntry(at, event, actor));
    }

    public void resubmit(CandidateProfile updated, String newPitch, Instant at) {
        this.candidate = updated;
        this.pitch = newPitch == null ? "" : newPitch.trim();
        this.status = RequestStatus.PENDING;
        this.updatedAt = at;
        timeline.add(new TimelineEntry(at, "Candidate updated details and resubmitted", Actor.SEEKER));
    }

    public void addEvent(Instant at, String text, Actor actor) {
        timeline.add(new TimelineEntry(at, text, actor));
    }

    public void addMessage(Instant at, Actor from, String text) {
        messages.add(new Message(at, from, text.trim()));
    }

    public void remind(Instant at) {
        lastReminderAt = at;
        timeline.add(new TimelineEntry(at, "Candidate sent a friendly reminder", Actor.SEEKER));
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
        if (lastReminderAt != null) {
            m.put("lastReminderAt", lastReminderAt.toString());
        }
        List<Object> events = new ArrayList<>();
        for (TimelineEntry e : timeline) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("at", e.at().toString());
            em.put("text", e.text());
            em.put("actor", e.actor().name());
            events.add(em);
        }
        m.put("timeline", events);
        List<Object> msgs = new ArrayList<>();
        for (Message msg : messages) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("at", msg.at().toString());
            mm.put("from", msg.from().name());
            mm.put("text", msg.text());
            msgs.add(mm);
        }
        m.put("messages", msgs);
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
        String reminded = Json.str(m, "lastReminderAt");
        r.lastReminderAt = reminded.isEmpty() ? null : Instant.parse(reminded);
        for (Object o : Json.arr(m, "timeline")) {
            Map<String, Object> em = Json.asObject(o);
            String actor = Json.str(em, "actor");
            String text = Json.str(em, "text");
            r.timeline.add(new TimelineEntry(Instant.parse(Json.str(em, "at")), text,
                    actor.isEmpty() ? TimelineEntry.guessActor(text) : Actor.valueOf(actor)));
        }
        for (Object o : Json.arr(m, "messages")) {
            Map<String, Object> mm = Json.asObject(o);
            r.messages.add(new Message(Instant.parse(Json.str(mm, "at")), Actor.valueOf(Json.str(mm, "from")),
                    Json.str(mm, "text")));
        }
        return r;
    }
}
