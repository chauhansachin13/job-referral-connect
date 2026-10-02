package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.service.AppServices;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

/** Home screen for referrers: their inbox, their company's openings, and settings. */
final class ReferrerDashboard extends JPanel implements AppFrame.Live {

    private final AppServices app;
    private final Account account;
    private final JTabbedPane tabs = new JTabbedPane();
    private final InboxPanel inbox;
    private final JobsPanel openings;

    ReferrerDashboard(AppServices app, Account account, Runnable onSignOut) {
        super(new BorderLayout());
        this.app = app;
        this.account = account;
        setBackground(Theme.BG);
        String role = "Referrer" + (account.designation().isEmpty() ? "" : " · " + account.designation())
                + " at " + account.companyName();
        add(new HeaderBar("Referral inbox for " + account.companyName(), account.name(), role, onSignOut),
                BorderLayout.NORTH);

        inbox = new InboxPanel(app, account, this::updateTitles);
        openings = new JobsPanel(app, account, JobsPanel.Mode.COMPANY, () -> { });
        tabs.setFont(Theme.BODY_BOLD);
        tabs.addTab("Referral inbox", inbox);
        tabs.addTab("Openings at " + account.companyName(), openings);
        tabs.addTab("Settings", new ProfilePanel(app, account, saved -> { }));
        tabs.setBorder(Ui.padding(8, 8, 0, 8));
        add(tabs, BorderLayout.CENTER);
        updateTitles();
    }

    @Override
    public void refreshData() {
        inbox.refreshData();
        openings.refreshData();
        updateTitles();
    }

    private void updateTitles() {
        long pending = app.referrals.pendingCount(account.id());
        tabs.setTitleAt(0, pending == 0 ? "Referral inbox" : "Referral inbox (" + pending + " pending)");
    }

    JTabbedPane tabs() {
        return tabs;
    }
}
