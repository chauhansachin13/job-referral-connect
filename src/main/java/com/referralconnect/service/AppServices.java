package com.referralconnect.service;

import com.referralconnect.scan.Http;
import com.referralconnect.store.DataStore;

import java.nio.file.Path;
import java.time.Clock;

/** Wires the store and services together once, for the GUI, the CLI and the tests. */
public final class AppServices {

    public final DataStore store;
    public final CompanyDirectory directory;
    public final AuthService auth;
    public final JobService jobs;
    public final ReferralService referrals;
    public final TrackerService tracker;
    public final AlertService alerts;
    public final PrefsService prefs;
    public final NotificationService notifications;

    public AppServices(Path home, Http http, Clock clock) {
        this.store = new DataStore(home);
        this.directory = new CompanyDirectory(store);
        this.auth = new AuthService(store, clock);
        this.jobs = new JobService(store, directory, http);
        this.referrals = new ReferralService(store, clock);
        this.tracker = new TrackerService(store, clock);
        this.alerts = new AlertService(store, clock);
        this.prefs = new PrefsService(store, clock);
        this.notifications = new NotificationService(store, alerts, clock);
    }

    public static AppServices live(Path home) {
        return new AppServices(home, Http.live(), Clock.systemUTC());
    }
}
