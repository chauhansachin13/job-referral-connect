package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.service.AppServices;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;

/** Everything a referrer does: insights, the referral inbox, their company's openings, and settings. */
final class ReferrerDashboard extends JPanel implements AppFrame.Live {

    private final Shell shell;
    private final InboxPanel inbox;

    ReferrerDashboard(AppServices app, Account account, AppFrame.Context ctx) {
        super(new BorderLayout());
        setBackground(Theme.BG);
        inbox = new InboxPanel(app, account, this::badgesChanged);
        JobsPanel openings = new JobsPanel(app, account, JobsPanel.Mode.COMPANY, () -> { });
        ProfilePanel settings = new ProfilePanel(app, account, ctx, saved -> badgesChanged());
        ReferrerHome home = new ReferrerHome(app, account, id -> {
            show("inbox");
            inbox.select(id);
        }, this::show);
        shell = new Shell(app, account, ctx, List.of(
                new Shell.Page("home", "Home", "Your referrals at a glance", Icons.Glyph.HOME, home, null),
                new Shell.Page("inbox", "Referral inbox", "Requests from candidates for openings at "
                        + account.companyName(), Icons.Glyph.INBOX, inbox,
                        () -> (int) app.referrals.pendingCount(account.id())),
                new Shell.Page("openings", "Openings at " + account.companyName(), "What candidates can ask you to "
                        + "refer them for, with each role's minimum experience", Icons.Glyph.BRIEFCASE, openings, null),
                new Shell.Page("settings", "Settings", "Your role, availability and app settings", Icons.Glyph.SLIDERS,
                        settings, null)));
        shell.setSearchAction(() -> {
            show("openings");
            openings.focusSearch();
        });
        shell.setNotificationAction(n -> {
            if (n.requestId() != null) {
                show("inbox");
                inbox.select(n.requestId());
            }
        });
        add(shell, BorderLayout.CENTER);
    }

    private void badgesChanged() {
        shell.updateBadges();
    }

    void show(String page) {
        shell.show(page);
    }

    String currentPage() {
        return shell.current();
    }

    void dispose() {
        shell.dispose();
    }

    @Override
    public void refreshData() {
        shell.refreshData();
    }

    Shell shell() {
        return shell;
    }
}
