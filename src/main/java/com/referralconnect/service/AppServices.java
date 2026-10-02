package com.referralconnect.service;

import com.referralconnect.scan.JobScanner;
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

    public AppServices(Path home, JobScanner.Fetcher fetcher, Clock clock) {
        this.store = new DataStore(home);
        this.directory = new CompanyDirectory(store);
        this.auth = new AuthService(store, clock);
        this.jobs = new JobService(store, directory, fetcher);
        this.referrals = new ReferralService(store, clock);
    }

    public static AppServices live(Path home) {
        return new AppServices(home, JobScanner.httpFetcher(), Clock.systemUTC());
    }
}
