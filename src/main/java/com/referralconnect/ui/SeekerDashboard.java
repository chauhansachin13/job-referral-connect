package com.referralconnect.ui;

import com.referralconnect.model.Account;
import com.referralconnect.model.JobAlert;
import com.referralconnect.model.RequestStatus;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.NotificationService;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Everything a job seeker does: a home dashboard, the openings with their minimum experience,
 * a tracker for saved and applied jobs, referral requests with messages, job alerts, the company
 * list and their profile.
 */
final class SeekerDashboard extends JPanel implements AppFrame.Live {

    private final AppServices app;
    private final Account account;
    private final AppFrame.Context ctx;
    private final Shell shell;
    private final SeekerHome home;
    private final JobsPanel jobs;
    private final TrackerPanel tracker;
    private final SeekerRequestsPanel requests;
    private final AlertsPanel alerts;
    private final CompaniesPanel companies;

    SeekerDashboard(AppServices app, Account account, AppFrame.Context ctx) {
        super(new BorderLayout());
        this.app = app;
        this.account = account;
        this.ctx = ctx;
        setBackground(Theme.BG);

        Runnable changed = this::badgesChanged;
        jobs = new JobsPanel(app, account, JobsPanel.Mode.SEEKER, changed);
        tracker = new TrackerPanel(app, account, job -> {
            show("openings");
            jobs.select(job.id());
        }, changed);
        requests = new SeekerRequestsPanel(app, account, changed);
        jobs.onOpenRequest(id -> {
            show("requests");
            requests.select(id);
        });
        alerts = new AlertsPanel(app, account, this::openAlert, changed);
        companies = new CompaniesPanel(app, name -> {
            show("openings");
            jobs.showCompany(name);
        });
        ProfilePanel profile = new ProfilePanel(app, account, ctx, saved -> {
            jobs.refreshData();
            badgesChanged();
        });
        home = new SeekerHome(app, account, new SeekerHome.Nav() {
            @Override
            public void openings(JobsPanel.Preset preset) {
                show("openings");
                jobs.applyPreset(preset);
            }

            @Override
            public void openJob(String jobId) {
                show("openings");
                jobs.select(jobId);
            }

            @Override
            public void page(String pageId) {
                show(pageId);
            }

            @Override
            public void notification(NotificationService.Notification n) {
                openNotification(n);
            }
        });

        shell = new Shell(app, account, ctx, List.of(
                new Shell.Page("home", "Home", "Your job search at a glance", Icons.Glyph.HOME, home, null),
                new Shell.Page("openings", "Openings", "Recent tech jobs and internships in India, with the minimum "
                        + "experience each one asks for", Icons.Glyph.BRIEFCASE, jobs, this::newOpenings),
                new Shell.Page("tracker", "Saved & applied", "Your application pipeline — move cards as you progress",
                        Icons.Glyph.KANBAN, tracker, null),
                new Shell.Page("requests", "Referral requests", "What referrers said, and your conversations with them",
                        Icons.Glyph.SEND, requests, this::requestsNeedingMe),
                new Shell.Page("alerts", "Job alerts", "Saved searches that count new openings after every scan",
                        Icons.Glyph.BELL, alerts, alerts::newCount),
                new Shell.Page("companies", "Companies", app.directory.all().size()
                        + " MNCs and startups hiring in India, and how each careers site is doing",
                        Icons.Glyph.BUILDING, companies, null),
                new Shell.Page("profile", "Profile & settings", "What referrers see, your preferences and app settings",
                        Icons.Glyph.USER, profile, () -> app.auth.require(account.id()).profile().missingForReferral()
                        .isEmpty() ? 0 : 1)));
        shell.setSearchAction(() -> {
            show("openings");
            jobs.focusSearch();
        });
        shell.setNotificationAction(this::openNotification);
        add(shell, BorderLayout.CENTER);
    }

    private int newOpenings() {
        Instant since = app.prefs.of(account.id()).previousVisitAt();
        Map<String, Instant> seen = app.jobs.firstSeen();
        return (int) app.jobs.jobs().stream().filter(j -> seen.getOrDefault(j.id(), j.postedAt()).isAfter(since)).count();
    }

    /** Requests where the seeker has something to do: info requested, or an unanswered message. */
    private int requestsNeedingMe() {
        return (int) app.referrals.forSeeker(account.id()).stream()
                .filter(r -> r.status() == RequestStatus.NEEDS_INFO || !r.messages().isEmpty()
                        && r.messages().get(r.messages().size() - 1).from() == com.referralconnect.model.ReferralRequest.Actor.REFERRER
                        && r.status() != RequestStatus.WITHDRAWN)
                .count();
    }

    private void openAlert(JobAlert a) {
        show("openings");
        jobs.applyPreset(new JobsPanel.Preset(a.query(), null, a.category(), a.internship(), a.city(), a.maxYears(),
                30, false, false, JobsPanel.Sort.NEWEST));
    }

    private void openNotification(NotificationService.Notification n) {
        if (n.requestId() != null) {
            show("requests");
            requests.select(n.requestId());
        } else if (n.alertId() != null) {
            app.alerts.list(account.id()).stream().filter(a -> a.id().equals(n.alertId())).findFirst().ifPresent(a -> {
                app.alerts.markChecked(account.id(), a.id());
                openAlert(a);
            });
        }
        badgesChanged();
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

    /** Called once the dashboard is on screen: scans if the results are stale. */
    void onShown() {
        if (app.jobs.isStale(Instant.now()) && (app.prefs.autoScan() || app.jobs.lastScanAt() == null)) {
            ctx.scans().start(this);
        }
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

    JobsPanel jobs() {
        return jobs;
    }
}
