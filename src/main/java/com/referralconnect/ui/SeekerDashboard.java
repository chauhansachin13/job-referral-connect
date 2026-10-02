package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.service.AppServices;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

/** Home screen for job seekers: openings, sent requests and their referral profile. */
final class SeekerDashboard extends JPanel implements AppFrame.Live {

    private final JTabbedPane tabs = new JTabbedPane();
    private final JobsPanel jobs;
    private final SeekerRequestsPanel requests;

    SeekerDashboard(AppServices app, Account account, Runnable onSignOut) {
        super(new BorderLayout());
        setBackground(Theme.BG);
        add(new HeaderBar("Recent jobs & internships in India · get referred", account.name(), "Job seeker",
                onSignOut), BorderLayout.NORTH);

        jobs = new JobsPanel(app, account, JobsPanel.Mode.SEEKER, this::refreshData);
        requests = new SeekerRequestsPanel(app, account, this::refreshData);
        ProfilePanel profile = new ProfilePanel(app, account, saved -> {
            markProfile(saved);
            jobs.refreshData();
        });

        tabs.setFont(Theme.BODY_BOLD);
        tabs.addTab("Openings", jobs);
        tabs.addTab("My referral requests", requests);
        tabs.addTab("My profile", profile);
        tabs.setBorder(Ui.padding(8, 8, 0, 8));
        add(tabs, BorderLayout.CENTER);
        updateTitles();
        markProfile(account);
    }

    /** Flags the profile tab until it has everything a referrer needs. */
    private void markProfile(Account account) {
        boolean incomplete = !account.profile().missingForReferral().isEmpty();
        tabs.setTitleAt(2, incomplete ? "My profile  (incomplete)" : "My profile");
    }

    /** Called once the dashboard is on screen. */
    void onShown() {
        jobs.scanIfStale();
    }

    @Override
    public void refreshData() {
        jobs.refreshData();
        requests.refreshData();
        updateTitles();
    }

    private void updateTitles() {
        long open = requests.openCount();
        tabs.setTitleAt(1, open == 0 ? "My referral requests" : "My referral requests (" + open + " open)");
    }

    JTabbedPane tabs() {
        return tabs;
    }
}
