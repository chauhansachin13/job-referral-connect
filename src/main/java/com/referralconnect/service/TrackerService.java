package com.referralconnect.service;

import com.referralconnect.model.JobPosting;
import com.referralconnect.model.TrackedJob;
import com.referralconnect.model.TrackedJob.Stage;
import com.referralconnect.store.DataStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Saved jobs and the seeker's own application pipeline: Saved → Applied → Interviewing → Offer. */
public final class TrackerService {

    /** Keeps the board manageable; old closed entries can be removed to make room. */
    public static final int MAX_TRACKED = 300;

    private final DataStore store;
    private final Clock clock;

    public TrackerService(DataStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** Adds the job at {@code stage}, or moves it there if it is already tracked. */
    public TrackedJob track(String accountId, JobPosting job, Stage stage) {
        Instant now = clock.instant();
        return store.write(s -> {
            Optional<TrackedJob> existing = find(s, accountId, job.id());
            if (existing.isPresent()) {
                existing.get().move(stage, now);
                return existing.get();
            }
            long mine = s.tracked.stream().filter(t -> t.accountId().equals(accountId)).count();
            if (mine >= MAX_TRACKED) {
                throw new ServiceException("You are tracking " + mine + " jobs. Remove a few old ones first.");
            }
            TrackedJob t = new TrackedJob(accountId, job, now, stage, "", now);
            s.tracked.add(t);
            return t;
        });
    }

    public TrackedJob save(String accountId, JobPosting job) {
        return track(accountId, job, Stage.SAVED);
    }

    public TrackedJob move(String accountId, String jobId, Stage stage) {
        Instant now = clock.instant();
        return store.write(s -> {
            TrackedJob t = find(s, accountId, jobId)
                    .orElseThrow(() -> new ServiceException("That job is no longer on your board."));
            t.move(stage, now);
            return t;
        });
    }

    public TrackedJob note(String accountId, String jobId, String note) {
        if (note != null && note.length() > 1000) {
            throw new ServiceException("Notes can be at most 1000 characters.");
        }
        Instant now = clock.instant();
        return store.write(s -> {
            TrackedJob t = find(s, accountId, jobId)
                    .orElseThrow(() -> new ServiceException("That job is no longer on your board."));
            t.setNote(note, now);
            return t;
        });
    }

    public void remove(String accountId, String jobId) {
        store.update(s -> s.tracked.removeIf(t -> t.accountId().equals(accountId) && t.job().id().equals(jobId)));
    }

    /** Most recently changed first. */
    public List<TrackedJob> list(String accountId) {
        return store.read(s -> s.tracked.stream()
                .filter(t -> t.accountId().equals(accountId))
                .sorted(Comparator.comparing(TrackedJob::updatedAt).reversed())
                .toList());
    }

    /** jobId → stage, for marking tracked jobs in lists. */
    public Map<String, Stage> stages(String accountId) {
        return store.read(s -> {
            Map<String, Stage> out = new java.util.HashMap<>();
            s.tracked.stream().filter(t -> t.accountId().equals(accountId))
                    .forEach(t -> out.put(t.job().id(), t.stage()));
            return out;
        });
    }

    public Map<Stage, Long> counts(String accountId) {
        Map<Stage, Long> out = new EnumMap<>(Stage.class);
        for (Stage st : Stage.values()) {
            out.put(st, 0L);
        }
        list(accountId).forEach(t -> out.merge(t.stage(), 1L, Long::sum));
        return out;
    }

    private static Optional<TrackedJob> find(DataStore.State s, String accountId, String jobId) {
        return s.tracked.stream()
                .filter(t -> t.accountId().equals(accountId) && t.job().id().equals(jobId))
                .findFirst();
    }
}
