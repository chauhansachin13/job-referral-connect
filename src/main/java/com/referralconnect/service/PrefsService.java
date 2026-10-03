package com.referralconnect.service;

import com.referralconnect.model.JobCategory;
import com.referralconnect.model.JobPosting;
import com.referralconnect.model.UserPrefs;
import com.referralconnect.store.DataStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/** Per-account preferences and app-wide display settings. */
public final class PrefsService {

    private final DataStore store;
    private final Clock clock;

    public PrefsService(DataStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public UserPrefs get(String accountId) {
        return store.read(s -> s.prefs.get(accountId));
    }

    /** The account's prefs, never null: a fresh set if it has none yet. */
    public UserPrefs of(String accountId) {
        UserPrefs p = get(accountId);
        return p != null ? p : UserPrefs.fresh(clock.instant());
    }

    /**
     * Called at sign-in: the previous session's start becomes the line between "new" and "seen"
     * openings. Also forgets hidden jobs that have left the scan window.
     */
    public UserPrefs startVisit(String accountId) {
        Instant now = clock.instant();
        return store.write(s -> {
            UserPrefs current = s.prefs.get(accountId);
            Set<String> ids = s.jobs.stream().map(JobPosting::id).collect(Collectors.toSet());
            UserPrefs next = current == null ? UserPrefs.fresh(now) : current.startVisit(now).keepHidden(ids);
            s.prefs.put(accountId, next);
            return next;
        });
    }

    public UserPrefs markNotificationsRead(String accountId) {
        Instant now = clock.instant();
        return change(accountId, p -> p.readNotifications(now));
    }

    public UserPrefs hide(String accountId, String jobId, boolean hidden) {
        return change(accountId, p -> p.hide(jobId, hidden));
    }

    public UserPrefs setPreferences(String accountId, Set<JobCategory> roles, Set<String> cities) {
        return change(accountId, p -> p.withPreferences(roles, cities));
    }

    private UserPrefs change(String accountId, java.util.function.UnaryOperator<UserPrefs> f) {
        Instant now = clock.instant();
        return store.write(s -> {
            UserPrefs next = f.apply(s.prefs.getOrDefault(accountId, UserPrefs.fresh(now)));
            s.prefs.put(accountId, next);
            return next;
        });
    }

    // ---------------------------------------------------------------- app-wide settings

    public boolean darkMode() {
        return store.read(s -> s.darkMode);
    }

    public void setDarkMode(boolean on) {
        store.update(s -> s.darkMode = on);
    }

    /** Scan again automatically while the app is open, whenever results are older than 30 minutes. */
    public boolean autoScan() {
        return store.read(s -> s.autoScan);
    }

    public void setAutoScan(boolean on) {
        store.update(s -> s.autoScan = on);
    }
}
