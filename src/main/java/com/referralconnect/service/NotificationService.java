package com.referralconnect.service;

import com.referralconnect.model.Account;
import com.referralconnect.model.JobAlert;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.ReferralRequest.Actor;
import com.referralconnect.model.UserPrefs;
import com.referralconnect.store.DataStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What happened that an account should know about, built from the requests, messages and alerts
 * themselves: nothing is stored apart from when the account last read its notifications.
 */
public final class NotificationService {

    /** Older notifications are not listed. */
    public static final Duration HORIZON = Duration.ofDays(30);

    public enum Kind { STATUS, MESSAGE, NEW_REQUEST, REMINDER, ALERT }

    /**
     * @param requestId the request it is about, if any
     * @param alertId   the alert it is about, if any
     */
    public record Notification(Instant at, Kind kind, String title, String body, String requestId, String alertId,
                               boolean unread) {
    }

    private final DataStore store;
    private final AlertService alerts;
    private final Clock clock;

    public NotificationService(DataStore store, AlertService alerts, Clock clock) {
        this.store = store;
        this.alerts = alerts;
        this.clock = clock;
    }

    /** Newest first. */
    public List<Notification> list(Account account) {
        Instant now = clock.instant();
        Instant since = now.minus(HORIZON);
        UserPrefs prefs = store.read(s -> s.prefs.get(account.id()));
        Instant readAt = prefs == null ? Instant.EPOCH : prefs.notificationsReadAt();
        List<Notification> out = new ArrayList<>();
        List<ReferralRequest> requests = store.read(s -> s.requests.stream()
                .filter(r -> r.seekerId().equals(account.id()) || r.referrerId().equals(account.id()))
                .toList());
        for (ReferralRequest r : requests) {
            boolean asSeeker = r.seekerId().equals(account.id());
            Actor other = asSeeker ? Actor.REFERRER : Actor.SEEKER;
            String about = r.job().title() + " · " + r.job().company();
            if (!asSeeker && r.createdAt().isAfter(since)) {
                out.add(new Notification(r.createdAt(), Kind.NEW_REQUEST,
                        "New referral request from " + r.candidate().name(), about, r.id(), null,
                        r.createdAt().isAfter(readAt)));
            }
            for (ReferralRequest.TimelineEntry e : r.timeline()) {
                if (e.actor() != other || e.at().isBefore(since) || e.text().startsWith("Request sent")) {
                    continue;
                }
                Kind kind = e.text().contains("reminder") ? Kind.REMINDER : Kind.STATUS;
                String title = asSeeker ? statusTitle(e.text(), r) : seekerEventTitle(e.text(), r);
                String body = asSeeker && !r.referrerNote().isEmpty() && !e.text().startsWith("Referrer opened")
                        ? about + " — \"" + r.referrerNote() + "\"" : about;
                out.add(new Notification(e.at(), kind, title, body, r.id(), null, e.at().isAfter(readAt)));
            }
            for (ReferralRequest.Message m : r.messages()) {
                if (m.from() != other || m.at().isBefore(since)) {
                    continue;
                }
                String who = asSeeker ? r.referrerName() : r.candidate().name();
                out.add(new Notification(m.at(), Kind.MESSAGE, "Message from " + who,
                        about + " — \"" + shorten(m.text()) + "\"", r.id(), null, m.at().isAfter(readAt)));
            }
        }
        if (!account.isReferrer()) {
            for (JobAlert a : alerts.list(account.id())) {
                int fresh = alerts.newMatches(a).size();
                if (fresh > 0) {
                    Instant at = store.read(s -> s.lastScanAt) == null ? now : store.read(s -> s.lastScanAt);
                    out.add(new Notification(at, Kind.ALERT, fresh + (fresh == 1 ? " new opening" : " new openings")
                            + " for \"" + a.name() + "\"", a.describe(), null, a.id(), at.isAfter(readAt)));
                }
            }
        }
        out.sort(Comparator.comparing(Notification::at).reversed());
        return out;
    }

    public long unreadCount(Account account) {
        return list(account).stream().filter(Notification::unread).count();
    }

    private static String statusTitle(String event, ReferralRequest r) {
        if (event.startsWith("Referred")) {
            return "You were referred at " + r.job().company() + "!";
        }
        if (event.startsWith("Referrer asked")) {
            return r.referrerName() + " needs more information";
        }
        if (event.startsWith("Referrer declined")) {
            return r.referrerName() + " couldn't refer you this time";
        }
        if (event.startsWith("Referrer opened")) {
            return r.referrerName() + " opened your request";
        }
        return event;
    }

    private static String seekerEventTitle(String event, ReferralRequest r) {
        String who = r.candidate().name();
        if (event.contains("resubmitted")) {
            return who + " sent the details you asked for";
        }
        if (event.contains("reminder")) {
            return who + " sent a reminder";
        }
        if (event.contains("withdrew")) {
            return who + " withdrew their request";
        }
        return event;
    }

    private static String shorten(String s) {
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() <= 90 ? one : one.substring(0, 87) + "…";
    }
}
