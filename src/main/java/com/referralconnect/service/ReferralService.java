package com.referralconnect.service;

import com.referralconnect.model.Account;
import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.JobPosting;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.ReferralRequest.Actor;
import com.referralconnect.model.RequestStatus;
import com.referralconnect.model.Role;
import com.referralconnect.store.DataStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The two-sided part of the system: seekers ask for referrals on scanned jobs, each request is
 * routed to one referrer at that company, and referrers act on the details they receive.
 */
public final class ReferralService {

    /** A short pitch is the one thing a referrer cannot get from a resume. */
    public static final int MIN_PITCH_LENGTH = 30;

    /** Stops one seeker from flooding a single company's referrers. */
    public static final int MAX_OPEN_PER_COMPANY = 3;

    private final DataStore store;
    private final Clock clock;

    public ReferralService(DataStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- seeker side

    /** Referrers at a company who are currently accepting requests. */
    public List<Account> referrersFor(String companyKey) {
        return store.read(s -> s.accounts.stream()
                .filter(a -> a.isReferrer() && a.acceptingRequests() && a.companyKey().equals(companyKey))
                .toList());
    }

    /** companyKey → number of referrers accepting requests there; drives the "Get referral" column. */
    public Map<String, Integer> referrerCountsByCompany() {
        return store.read(s -> s.accounts.stream()
                .filter(a -> a.isReferrer() && a.acceptingRequests())
                .collect(Collectors.groupingBy(Account::companyKey, Collectors.summingInt(a -> 1))));
    }

    /** jobId → status of the seeker's most recent request for that job. */
    public Map<String, RequestStatus> latestStatusByJob(String seekerId) {
        return store.read(s -> {
            Map<String, ReferralRequest> latest = new HashMap<>();
            for (ReferralRequest r : s.requests) {
                if (r.seekerId().equals(seekerId)) {
                    latest.merge(r.job().id(), r, (a, b) -> a.createdAt().isAfter(b.createdAt()) ? a : b);
                }
            }
            Map<String, RequestStatus> out = new HashMap<>();
            latest.forEach((jobId, r) -> out.put(jobId, r.status()));
            return out;
        });
    }

    public ReferralRequest requestReferral(String seekerId, JobPosting job, CandidateProfile candidate, String pitch) {
        List<String> missing = candidate.missingForReferral();
        if (!missing.isEmpty()) {
            throw new ServiceException("A referrer needs your " + String.join(", ", missing)
                    + ". Add them before sending.");
        }
        String cleanPitch = pitch == null ? "" : pitch.trim();
        if (cleanPitch.length() < MIN_PITCH_LENGTH) {
            throw new ServiceException("Tell the referrer why you fit this role (at least " + MIN_PITCH_LENGTH
                    + " characters).");
        }
        Instant now = clock.instant();
        return store.write(s -> {
            Account seeker = s.accounts.stream().filter(a -> a.id().equals(seekerId)).findFirst()
                    .orElseThrow(() -> new ServiceException("Account no longer exists."));
            if (seeker.role() != Role.SEEKER) {
                throw new ServiceException("Only job seekers can request referrals.");
            }
            Optional<ReferralRequest> live = s.requests.stream()
                    .filter(r -> r.seekerId().equals(seekerId) && r.job().id().equals(job.id()))
                    .filter(r -> r.status().isOpen() || r.status() == RequestStatus.REFERRED)
                    .findFirst();
            if (live.isPresent()) {
                throw new ServiceException("You already have a request for this job (" + live.get().status().label()
                        + ").");
            }
            long openAtCompany = s.requests.stream()
                    .filter(r -> r.seekerId().equals(seekerId) && r.status().isOpen())
                    .filter(r -> r.job().companyKey().equals(job.companyKey()))
                    .count();
            if (openAtCompany >= MAX_OPEN_PER_COMPANY) {
                throw new ServiceException("You already have " + openAtCompany + " open requests at " + job.company()
                        + ". Wait for a reply or withdraw one first.");
            }
            // A referrer who already declined this candidate for this job is not asked again.
            Set<String> declinedBy = s.requests.stream()
                    .filter(r -> r.seekerId().equals(seekerId) && r.job().id().equals(job.id()))
                    .filter(r -> r.status() == RequestStatus.DECLINED)
                    .map(ReferralRequest::referrerId)
                    .collect(Collectors.toSet());
            Map<String, Long> openLoad = s.requests.stream()
                    .filter(r -> r.status().isOpen())
                    .collect(Collectors.groupingBy(ReferralRequest::referrerId, Collectors.counting()));
            Account referrer = s.accounts.stream()
                    .filter(a -> a.isReferrer() && a.acceptingRequests() && a.companyKey().equals(job.companyKey()))
                    .filter(a -> !declinedBy.contains(a.id()))
                    .min(Comparator.comparingLong((Account a) -> openLoad.getOrDefault(a.id(), 0L))
                            .thenComparing(Account::createdAt))
                    .orElseThrow(() -> new ServiceException("No referrer at " + job.company()
                            + " is accepting requests right now."));

            ReferralRequest request = new ReferralRequest(newRequestId(), now, seekerId, referrer.id(),
                    referrer.referrerTitle(), job, candidate, cleanPitch);
            request.addEvent(now, "Request sent to " + referrer.referrerTitle(), Actor.SEEKER);
            s.requests.add(request);
            return request;
        });
    }

    public List<ReferralRequest> forSeeker(String seekerId) {
        return store.read(s -> s.requests.stream()
                .filter(r -> r.seekerId().equals(seekerId))
                .sorted(Comparator.comparing(ReferralRequest::updatedAt).reversed())
                .toList());
    }

    public ReferralRequest withdraw(String seekerId, String requestId) {
        Instant now = clock.instant();
        return store.write(s -> {
            ReferralRequest r = owned(s, requestId, seekerId, true);
            if (!r.status().isOpen()) {
                throw new ServiceException("Only open requests can be withdrawn.");
            }
            r.changeStatus(RequestStatus.WITHDRAWN, null, now, "Candidate withdrew the request", Actor.SEEKER);
            return r;
        });
    }

    /** Answers a "needs more info" with updated details; the request goes back to the referrer. */
    public ReferralRequest resubmit(String seekerId, String requestId, CandidateProfile candidate, String pitch) {
        List<String> missing = candidate.missingForReferral();
        if (!missing.isEmpty()) {
            throw new ServiceException("A referrer needs your " + String.join(", ", missing) + ".");
        }
        if (pitch == null || pitch.trim().length() < MIN_PITCH_LENGTH) {
            throw new ServiceException("Your note must be at least " + MIN_PITCH_LENGTH + " characters.");
        }
        Instant now = clock.instant();
        return store.write(s -> {
            ReferralRequest r = owned(s, requestId, seekerId, true);
            if (r.status() != RequestStatus.NEEDS_INFO) {
                throw new ServiceException("You can only resubmit when the referrer asked for more info.");
            }
            r.resubmit(candidate, pitch, now);
            return r;
        });
    }

    // ---------------------------------------------------------------- referrer side

    /** Open requests first (oldest waiting at the top), then closed ones, newest first. */
    public List<ReferralRequest> inbox(String referrerId) {
        return store.read(s -> s.requests.stream()
                .filter(r -> r.referrerId().equals(referrerId))
                .sorted(Comparator.comparing((ReferralRequest r) -> !r.status().isOpen())
                        .thenComparing((a, b) -> a.status().isOpen()
                                ? a.updatedAt().compareTo(b.updatedAt())
                                : b.updatedAt().compareTo(a.updatedAt())))
                .toList());
    }

    public long pendingCount(String referrerId) {
        return store.read(s -> s.requests.stream()
                .filter(r -> r.referrerId().equals(referrerId) && r.status() == RequestStatus.PENDING)
                .count());
    }

    public void markViewed(String referrerId, String requestId) {
        Instant now = clock.instant();
        boolean alreadyViewed = store.read(s -> s.requests.stream()
                .anyMatch(r -> r.id().equals(requestId) && r.viewedAt() != null));
        if (alreadyViewed) {
            return;
        }
        store.update(s -> owned(s, requestId, referrerId, false).markViewed(now));
    }

    public ReferralRequest respond(String referrerId, String requestId, RequestStatus next, String note) {
        if ((next == RequestStatus.NEEDS_INFO || next == RequestStatus.DECLINED) && (note == null || note.isBlank())) {
            throw new ServiceException(next == RequestStatus.NEEDS_INFO
                    ? "Say what extra information you need."
                    : "Add a short reason so the candidate can improve.");
        }
        Instant now = clock.instant();
        return store.write(s -> {
            ReferralRequest r = owned(s, requestId, referrerId, false);
            if (!r.status().referrerMoves().contains(next)) {
                throw new ServiceException("A request that is " + r.status().label().toLowerCase(Locale.ROOT)
                        + " cannot be marked " + next.label().toLowerCase(Locale.ROOT) + ".");
            }
            String event = switch (next) {
                case REFERRED -> "Referred by " + r.referrerTitle();
                case NEEDS_INFO -> "Referrer asked for more information";
                case DECLINED -> "Referrer declined";
                default -> next.label();
            };
            r.markViewed(now);
            r.changeStatus(next, note, now, event, Actor.REFERRER);
            return r;
        });
    }

    // ---------------------------------------------------------------- conversation

    /** Longest message either side can send on a request. */
    public static final int MAX_MESSAGE_LENGTH = 2000;

    /** A seeker may nudge a referrer about a pending request this long after the last activity. */
    public static final Duration REMIND_AFTER = Duration.ofDays(3);

    /** Adds a message from the seeker or the referrer of this request; the other side is notified. */
    public ReferralRequest sendMessage(String accountId, String requestId, String text) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            throw new ServiceException("Write a message first.");
        }
        if (clean.length() > MAX_MESSAGE_LENGTH) {
            throw new ServiceException("Messages can be at most " + MAX_MESSAGE_LENGTH + " characters.");
        }
        Instant now = clock.instant();
        return store.write(s -> {
            ReferralRequest r = s.requests.stream().filter(x -> x.id().equals(requestId)).findFirst()
                    .orElseThrow(() -> new ServiceException("That request no longer exists."));
            Actor from = accountId.equals(r.seekerId()) ? Actor.SEEKER
                    : accountId.equals(r.referrerId()) ? Actor.REFERRER : null;
            if (from == null) {
                throw new ServiceException("That request belongs to someone else.");
            }
            if (r.status() == RequestStatus.WITHDRAWN) {
                throw new ServiceException("This request was withdrawn, so the conversation is closed.");
            }
            r.addMessage(now, from, clean);
            return r;
        });
    }

    /** When the seeker may next send a reminder, or null if a reminder makes no sense now. */
    public static Instant reminderAllowedAt(ReferralRequest r) {
        if (r.status() != RequestStatus.PENDING) {
            return null;
        }
        Instant since = r.lastActivityAt();
        return since.plus(REMIND_AFTER);
    }

    /** Nudges the referrer about a pending request that has been quiet for {@link #REMIND_AFTER}. */
    public ReferralRequest remind(String seekerId, String requestId) {
        Instant now = clock.instant();
        return store.write(s -> {
            ReferralRequest r = owned(s, requestId, seekerId, true);
            Instant allowed = reminderAllowedAt(r);
            if (allowed == null) {
                throw new ServiceException("Reminders are only for requests still waiting on the referrer.");
            }
            if (now.isBefore(allowed)) {
                long hours = Math.max(1, Duration.between(now, allowed).toHours());
                throw new ServiceException("Give the referrer a little more time — you can send a reminder in "
                        + (hours >= 24 ? (hours + 23) / 24 + " day(s)." : hours + " hour(s)."));
            }
            r.remind(now);
            return r;
        });
    }

    // ---------------------------------------------------------------- insights

    /**
     * A referrer's track record.
     *
     * @param responseRate        share of requests (not withdrawn) that got a decision or a question, 0–100
     * @param medianResponseHours median time from request to the referrer's first action, or -1
     * @param perWeek             requests received in each of the last 8 weeks, oldest first
     */
    public record ReferrerStats(int total, int pending, int needsInfo, int referred, int declined, int withdrawn,
                                int responseRate, long medianResponseHours, int[] perWeek) {
    }

    public ReferrerStats stats(String referrerId) {
        Instant now = clock.instant();
        return store.read(s -> {
            List<ReferralRequest> mine = s.requests.stream().filter(r -> r.referrerId().equals(referrerId)).toList();
            Map<RequestStatus, Long> by = mine.stream()
                    .collect(Collectors.groupingBy(ReferralRequest::status, Collectors.counting()));
            List<Long> hours = new ArrayList<>();
            int answerable = 0;
            int answered = 0;
            int[] perWeek = new int[8];
            for (ReferralRequest r : mine) {
                long weeksAgo = Duration.between(r.createdAt(), now).toDays() / 7;
                if (weeksAgo >= 0 && weeksAgo < 8) {
                    perWeek[7 - (int) weeksAgo]++;
                }
                if (r.status() == RequestStatus.WITHDRAWN) {
                    continue;
                }
                answerable++;
                r.timeline().stream()
                        .filter(e -> e.actor() == Actor.REFERRER && !e.text().startsWith("Referrer opened"))
                        .findFirst()
                        .ifPresent(e -> hours.add(Duration.between(r.createdAt(), e.at()).toHours()));
            }
            answered = hours.size();
            Collections.sort(hours);
            return new ReferrerStats(mine.size(),
                    by.getOrDefault(RequestStatus.PENDING, 0L).intValue(),
                    by.getOrDefault(RequestStatus.NEEDS_INFO, 0L).intValue(),
                    by.getOrDefault(RequestStatus.REFERRED, 0L).intValue(),
                    by.getOrDefault(RequestStatus.DECLINED, 0L).intValue(),
                    by.getOrDefault(RequestStatus.WITHDRAWN, 0L).intValue(),
                    answerable == 0 ? 0 : Math.round(100f * answered / answerable),
                    hours.isEmpty() ? -1 : hours.get(hours.size() / 2),
                    perWeek);
        });
    }

    /** Requests where the other side wrote last, i.e. waiting for this account to read or answer. */
    public long unansweredMessages(String accountId) {
        return store.read(s -> s.requests.stream()
                .filter(r -> r.seekerId().equals(accountId) || r.referrerId().equals(accountId))
                .filter(r -> !r.messages().isEmpty())
                .filter(r -> {
                    Actor last = r.messages().get(r.messages().size() - 1).from();
                    return last == (r.seekerId().equals(accountId) ? Actor.REFERRER : Actor.SEEKER);
                })
                .count());
    }

    // ---------------------------------------------------------------- shared

    public Optional<ReferralRequest> find(String requestId) {
        return store.read(s -> s.requests.stream().filter(r -> r.id().equals(requestId)).findFirst());
    }

    private static ReferralRequest owned(DataStore.State s, String requestId, String accountId, boolean asSeeker) {
        ReferralRequest r = s.requests.stream().filter(x -> x.id().equals(requestId)).findFirst()
                .orElseThrow(() -> new ServiceException("That request no longer exists."));
        String owner = asSeeker ? r.seekerId() : r.referrerId();
        if (!owner.equals(accountId)) {
            throw new ServiceException("That request belongs to someone else.");
        }
        return r;
    }

    private static String newRequestId() {
        return "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    /** Plain-text referral packet a referrer can paste into their company's internal referral form. */
    public static String packet(ReferralRequest r) {
        CandidateProfile c = r.candidate();
        JobPosting j = r.job();
        StringBuilder sb = new StringBuilder();
        sb.append("REFERRAL REQUEST ").append(r.id()).append('\n');
        sb.append("Received: ").append(DATE.format(r.createdAt())).append("   Status: ").append(r.status().label())
                .append("\n\n");
        sb.append("JOB\n");
        line(sb, "Role", j.title());
        line(sb, "Company", j.company());
        line(sb, "Location", j.location());
        line(sb, "Type", j.typeLabel() + " · " + j.category().label());
        line(sb, j.dateKnown() ? "Posted" : "First seen", DATE.format(j.postedAt()));
        line(sb, "Min. exp.", j.internship() && j.requirements().minYears() <= 0 ? "Internship (students)"
                : j.requirements().longLabel());
        line(sb, "Degree", j.requirements().degree());
        line(sb, "Batch", j.requirements().batch());
        line(sb, "Job link", j.url());
        sb.append("\nCANDIDATE\n");
        line(sb, "Name", c.name());
        line(sb, "Email", c.email());
        line(sb, "Phone", c.phone());
        line(sb, "LinkedIn", c.linkedin());
        line(sb, "GitHub", c.github());
        line(sb, "Resume", c.resumeLink());
        line(sb, "Education", c.education());
        line(sb, "Total exp.", c.yearsLabel());
        line(sb, "Experience", c.experience());
        line(sb, "Skills", c.skills());
        sb.append("\nWHY I'M A GOOD FIT\n").append(r.pitch()).append('\n');
        return sb.toString();
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(String.format("  %-11s %s%n", label + ":", value));
        }
    }
}
