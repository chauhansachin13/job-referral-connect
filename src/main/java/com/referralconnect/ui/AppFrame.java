package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.JobAlert;
import com.referralconnect.service.AppServices;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.Instant;

/**
 * The single application window. Shows the sign-in screen, then the seeker or referrer
 * dashboard, and keeps it live: it polls the shared data file for changes made elsewhere (e.g. a
 * referrer acting on a request in another window) and re-scans careers sites every 30 minutes
 * when auto-scan is on.
 */
public final class AppFrame extends JFrame {

    /** Something on screen that can redraw itself from the latest data. */
    public interface Live {
        void refreshData();
    }

    /** Window-wide services a signed-in screen uses: scanning, the theme switch and signing out. */
    record Context(ScanController scans, Runnable toggleTheme, Runnable signOut) {
    }

    /** How often the auto-scan timer checks whether results have gone stale. */
    private static final int AUTO_SCAN_CHECK_MS = 60_000;

    private final AppServices app;
    private final JPanel root = new JPanel(new BorderLayout());
    private final ScanController scans;
    private final Context ctx;
    private Live current;
    private Account signedIn;
    private long seenVersion;

    private AppFrame(AppServices app) {
        super("Job Referral Connect");
        this.app = app;
        this.scans = new ScanController(app);
        this.ctx = new Context(scans, this::toggleTheme, this::showSignIn);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 740));
        setSize(1400, 880);
        setLocationRelativeTo(null);
        root.setBackground(Theme.BG);
        setContentPane(root);
        scans.onFinished(this::afterScan);
        showSignIn();

        Timer poll = new Timer(3000, e -> {
            try {
                long v = app.store.poll();
                if (v != seenVersion) {
                    seenVersion = v;
                    if (current != null) {
                        current.refreshData();
                    }
                }
            } catch (RuntimeException ex) {
                // A half-written file from another window: try again on the next tick.
            }
        });
        poll.start();
        Timer autoScan = new Timer(AUTO_SCAN_CHECK_MS, e -> {
            if (signedIn != null && !scans.running() && app.prefs.autoScan() && app.jobs.isStale(Instant.now())) {
                scans.start(this);
            }
        });
        autoScan.start();
    }

    public static void open(AppServices app) {
        Theme.apply(app.prefs.darkMode());
        AppFrame frame = new AppFrame(app);
        frame.setVisible(true);
    }

    void showSignIn() {
        disposeCurrent();
        signedIn = null;
        current = null;
        swap(new AuthPanel(app, this::showDashboard));
    }

    void showDashboard(Account account) {
        app.prefs.startVisit(account.id());
        signedIn = account;
        JComponent view = buildDashboard(account, null);
        if (view instanceof SeekerDashboard d) {
            d.onShown();
        }
    }

    private JComponent buildDashboard(Account account, String page) {
        disposeCurrent();
        JComponent view;
        if (account.isReferrer()) {
            ReferrerDashboard d = new ReferrerDashboard(app, account, ctx);
            if (page != null) {
                d.show(page);
            }
            current = d;
            view = d;
        } else {
            SeekerDashboard d = new SeekerDashboard(app, account, ctx);
            if (page != null) {
                d.show(page);
            }
            current = d;
            view = d;
        }
        swap(view);
        seenVersion = app.store.poll();
        return view;
    }

    /** Switches light ↔ dark and rebuilds the screen on the same page. */
    void toggleTheme() {
        boolean dark = !Theme.isDark();
        app.prefs.setDarkMode(dark);
        Theme.apply(dark);
        String page = current instanceof SeekerDashboard s ? s.currentPage()
                : current instanceof ReferrerDashboard r ? r.currentPage() : null;
        root.setBackground(Theme.BG);
        // The screen is rebuilt from scratch, so its widgets pick up the new colours as they are made.
        if (signedIn != null) {
            buildDashboard(app.auth.require(signedIn.id()), page);
        } else {
            swap(new AuthPanel(app, this::showDashboard));
        }
    }

    /** After a scan: tell a seeker how many new openings matched their alerts. */
    private void afterScan() {
        if (signedIn == null || signedIn.isReferrer()) {
            return;
        }
        int fresh = 0;
        for (JobAlert a : app.alerts.list(signedIn.id())) {
            fresh += app.alerts.newMatches(a).size();
        }
        if (fresh > 0 && isShowing()) {
            Ui.toast(root, fresh + (fresh == 1 ? " new opening matches" : " new openings match")
                    + " your job alerts. See Job alerts.", Toast.Tone.INFO);
        }
    }

    private void disposeCurrent() {
        if (current instanceof SeekerDashboard s) {
            s.dispose();
        } else if (current instanceof ReferrerDashboard r) {
            r.dispose();
        }
    }

    private void swap(JComponent view) {
        root.removeAll();
        root.add(view, BorderLayout.CENTER);
        root.revalidate();
        root.repaint();
    }
}
