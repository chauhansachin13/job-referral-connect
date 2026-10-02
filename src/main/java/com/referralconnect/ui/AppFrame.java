package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.service.AppServices;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * The single application window. Shows the sign-in screen, then the seeker or referrer
 * dashboard, and keeps it live by polling the shared data file for changes made elsewhere
 * (e.g. a referrer acting on a request in another window).
 */
public final class AppFrame extends JFrame {

    /** Something on screen that can redraw itself from the latest data. */
    public interface Live {
        void refreshData();
    }

    private final AppServices app;
    private final JPanel root = new JPanel(new BorderLayout());
    private Live current;
    private long seenVersion;

    private AppFrame(AppServices app) {
        super("Job Referral Connect");
        this.app = app;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 720));
        setSize(1320, 840);
        setLocationRelativeTo(null);
        root.setBackground(Theme.BG);
        setContentPane(root);
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
    }

    public static void open(AppServices app) {
        Theme.install();
        AppFrame frame = new AppFrame(app);
        frame.setVisible(true);
    }

    void showSignIn() {
        current = null;
        swap(new AuthPanel(app, this::showDashboard));
    }

    void showDashboard(Account account) {
        if (account.isReferrer()) {
            ReferrerDashboard d = new ReferrerDashboard(app, account, this::showSignIn);
            current = d;
            swap(d);
        } else {
            SeekerDashboard d = new SeekerDashboard(app, account, this::showSignIn);
            current = d;
            swap(d);
            d.onShown();
        }
        seenVersion = app.store.poll();
    }

    private void swap(JComponent view) {
        root.removeAll();
        root.add(view, BorderLayout.CENTER);
        root.revalidate();
        root.repaint();
    }
}
