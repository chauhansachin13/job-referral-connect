package com.referralconnect.service;

import com.referralconnect.model.JobAlert;
import com.referralconnect.model.JobCategory;
import com.referralconnect.model.JobPosting;
import com.referralconnect.store.DataStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Saved searches that count new matching openings after every scan. */
public final class AlertService {

    public static final int MAX_ALERTS = 20;

    private final DataStore store;
    private final Clock clock;

    public AlertService(DataStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public JobAlert create(String accountId, String name, String query, JobCategory category, String city,
                           Boolean internship, int maxYears) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) {
            throw new ServiceException("Give the alert a name.");
        }
        Instant now = clock.instant();
        return store.write(s -> {
            List<JobAlert> mine = s.alerts.stream().filter(a -> a.accountId().equals(accountId)).toList();
            if (mine.size() >= MAX_ALERTS) {
                throw new ServiceException("You can keep up to " + MAX_ALERTS + " alerts. Delete one first.");
            }
            if (mine.stream().anyMatch(a -> a.name().equalsIgnoreCase(cleanName))) {
                throw new ServiceException("You already have an alert called \"" + cleanName + "\".");
            }
            JobAlert a = new JobAlert("ALERT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                    accountId, cleanName, query, category, city, internship, maxYears, now, now);
            s.alerts.add(a);
            return a;
        });
    }

    public void delete(String accountId, String alertId) {
        store.update(s -> s.alerts.removeIf(a -> a.accountId().equals(accountId) && a.id().equals(alertId)));
    }

    public List<JobAlert> list(String accountId) {
        return store.read(s -> s.alerts.stream()
                .filter(a -> a.accountId().equals(accountId))
                .sorted(Comparator.comparing(JobAlert::createdAt))
                .toList());
    }

    /** Every current opening the alert matches, newest first. */
    public List<JobPosting> matches(JobAlert alert) {
        return store.read(s -> s.jobs.stream().filter(alert::matches).toList());
    }

    /** Matching openings that a scan found after the alert was last opened. */
    public List<JobPosting> newMatches(JobAlert alert) {
        return store.read(s -> s.jobs.stream()
                .filter(alert::matches)
                .filter(j -> s.firstSeen.getOrDefault(j.id(), j.postedAt()).isAfter(alert.checkedAt()))
                .toList());
    }

    /** Marks everything the alert matches right now as seen. */
    public JobAlert markChecked(String accountId, String alertId) {
        Instant now = clock.instant();
        return store.write(s -> {
            for (int i = 0; i < s.alerts.size(); i++) {
                JobAlert a = s.alerts.get(i);
                if (a.id().equals(alertId) && a.accountId().equals(accountId)) {
                    JobAlert checked = a.checked(now);
                    s.alerts.set(i, checked);
                    return checked;
                }
            }
            throw new ServiceException("That alert no longer exists.");
        });
    }
}
