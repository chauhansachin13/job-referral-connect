package com.referralconnect;

import com.referralconnect.TestRunner.Test;
import com.referralconnect.model.Account;
import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.model.JobAlert;
import com.referralconnect.model.JobCategory;
import com.referralconnect.model.JobPosting;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.RequestStatus;
import com.referralconnect.model.Requirements;
import com.referralconnect.model.TrackedJob.Stage;
import com.referralconnect.model.UserPrefs;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.CompanyDirectory;
import com.referralconnect.service.CsvExport;
import com.referralconnect.service.JobMatcher;
import com.referralconnect.service.NotificationService;
import com.referralconnect.service.PitchWriter;
import com.referralconnect.service.ReferralService;
import com.referralconnect.service.ServiceException;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.referralconnect.TestRunner.check;
import static com.referralconnect.TestRunner.equal;
import static com.referralconnect.TestRunner.fails;

/** Saved jobs, alerts, notifications, conversations, reminders, matching and exports. */
class FeaturesTest {

    private final Path dir = Fixtures.tempDir();
    private final Fixtures.TestClock clock = new Fixtures.TestClock(Instant.parse("2026-10-01T09:00:00Z"));
    private final AppServices app = new AppServices(dir, (method, url, body) -> "{\"jobs\":[]}", clock);
    private final CompanyBoard mongo = CompanyDirectory.SEED.stream().filter(b -> b.name().equals("MongoDB"))
            .findFirst().orElseThrow();

    private static JobPosting withReq(JobPosting j, int min, List<String> skills) {
        return j.withRequirements(new Requirements(min, -1, Requirements.Basis.STATED, min + "+ years", "", "",
                skills, true));
    }

    private Account seeker() {
        Account a = app.auth.registerSeeker("Asha", "asha@example.com", "password1");
        return app.auth.updateProfile(a.id(), Fixtures.completeProfile("Asha", "asha@example.com").withYears(1));
    }

    @Test
    void trackerSavesMovesNotesAndRemoves() {
        try {
            Account s = seeker();
            JobPosting job = Fixtures.job(mongo.key(), "MongoDB", "1", "Software Engineer");
            app.tracker.save(s.id(), job);
            app.tracker.save(s.id(), job);
            equal(1, app.tracker.list(s.id()).size(), "saving twice keeps one card");
            clock.advance(Duration.ofHours(1));
            app.tracker.move(s.id(), job.id(), Stage.APPLIED);
            app.tracker.note(s.id(), job.id(), "Applied via careers site");
            equal(Stage.APPLIED, app.tracker.stages(s.id()).get(job.id()));
            equal("Applied via careers site", app.tracker.list(s.id()).get(0).note());
            equal(1L, app.tracker.counts(s.id()).get(Stage.APPLIED));
            check(CsvExport.tracker(app.tracker.list(s.id())).contains("Applied,MongoDB,Software Engineer"),
                    "tracker exports to CSV");
            app.tracker.remove(s.id(), job.id());
            check(app.tracker.list(s.id()).isEmpty(), "removed");
            fails(ServiceException.class, () -> app.tracker.move(s.id(), job.id(), Stage.OFFER));
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void alertsCountOnlyOpeningsFoundAfterTheyWereLastOpened() {
        try {
            Account s = seeker();
            JobPosting fresher = withReq(Fixtures.job("greenhouse:a", "A", "1", "Data Scientist"), 0, List.of("Python"));
            JobPosting senior = withReq(Fixtures.job("greenhouse:a", "A", "2", "Senior Data Scientist"), 6, List.of());
            app.store.update(st -> {
                st.jobs.add(fresher);
                st.jobs.add(senior);
                st.firstSeen.put(fresher.id(), clock.instant().minus(Duration.ofDays(1)));
                st.firstSeen.put(senior.id(), clock.instant().minus(Duration.ofDays(1)));
            });
            JobAlert alert = app.alerts.create(s.id(), "DS in Bengaluru", "scientist", null, "Bengaluru", false, 2);
            equal(1, app.alerts.matches(alert).size(), "the 6-year role is over the experience limit");
            equal(0, app.alerts.newMatches(alert).size(), "found before the alert existed");
            fails(ServiceException.class, () -> app.alerts.create(s.id(), "ds in bengaluru", "", null, "", null, -1));

            clock.advance(Duration.ofHours(2));
            JobPosting later = withReq(Fixtures.job("greenhouse:a", "A", "3", "Data Scientist II"), 2, List.of());
            app.store.update(st -> {
                st.jobs.add(later);
                st.firstSeen.put(later.id(), clock.instant());
            });
            JobAlert reloaded = app.alerts.list(s.id()).get(0);
            equal(1, app.alerts.newMatches(reloaded).size());
            List<NotificationService.Notification> notes = app.notifications.list(s);
            check(notes.stream().anyMatch(n -> n.kind() == NotificationService.Kind.ALERT
                    && n.title().startsWith("1 new opening")), "alert shows up as a notification");

            clock.advance(Duration.ofMinutes(1));
            app.alerts.markChecked(s.id(), alert.id());
            equal(0, app.alerts.newMatches(app.alerts.list(s.id()).get(0)).size());
            app.alerts.delete(s.id(), alert.id());
            check(app.alerts.list(s.id()).isEmpty(), "deleted");
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void conversationRemindersAndNotificationsFlowBothWays() {
        try {
            Account ref = app.auth.registerReferrer("Ravi", "ravi@mongo.example", "password1", mongo, "SDE-2");
            Account s = seeker();
            JobPosting job = Fixtures.job(mongo.key(), "MongoDB", "1", "Software Engineer");
            clock.advance(Duration.ofMinutes(5));
            ReferralRequest r = app.referrals.requestReferral(s.id(), job, s.profile(), Fixtures.PITCH);
            check(app.notifications.list(ref).stream().anyMatch(n -> n.kind() == NotificationService.Kind.NEW_REQUEST
                    && n.unread()), "referrer is told about the new request");

            fails(ServiceException.class, () -> app.referrals.remind(s.id(), r.id()), "too early for a reminder");
            clock.advance(ReferralService.REMIND_AFTER.plusMinutes(1));
            app.referrals.remind(s.id(), r.id());
            check(app.notifications.list(ref).stream().anyMatch(n -> n.kind() == NotificationService.Kind.REMINDER),
                    "the reminder reaches the referrer");
            fails(ServiceException.class, () -> app.referrals.remind(s.id(), r.id()), "one reminder per quiet spell");

            clock.advance(Duration.ofMinutes(5));
            app.referrals.sendMessage(ref.id(), r.id(), "Can you share your GitHub project link?");
            clock.advance(Duration.ofMinutes(5));
            app.referrals.sendMessage(s.id(), r.id(), "Sure: github.com/asha/payments");
            fails(ServiceException.class, () -> app.referrals.sendMessage("someone-else", r.id(), "hi"));
            fails(ServiceException.class, () -> app.referrals.sendMessage(s.id(), r.id(), "   "));
            ReferralRequest got = app.referrals.find(r.id()).orElseThrow();
            equal(2, got.messages().size());
            equal(ReferralRequest.Actor.REFERRER, got.messages().get(0).from());
            check(app.notifications.list(s).stream().anyMatch(n -> n.kind() == NotificationService.Kind.MESSAGE
                    && n.title().equals("Message from Ravi")), "seeker sees the referrer's message");
            equal(1L, app.referrals.unansweredMessages(ref.id()), "the seeker wrote last");

            clock.advance(Duration.ofHours(1));
            app.referrals.respond(ref.id(), r.id(), RequestStatus.REFERRED, "Submitted!");
            check(app.notifications.list(s).stream().anyMatch(n -> n.title().startsWith("You were referred")),
                    "seeker hears about the referral");
            long unread = app.notifications.unreadCount(s);
            check(unread > 0, "unread before reading");
            app.prefs.markNotificationsRead(s.id());
            equal(0L, app.notifications.unreadCount(s));

            ReferralService.ReferrerStats stats = app.referrals.stats(ref.id());
            equal(1, stats.total());
            equal(1, stats.referred());
            equal(100, stats.responseRate());
            check(stats.medianResponseHours() >= 72, "answered after the reminder, days later");
            equal(1, stats.perWeek()[7], "counted in the current week");

            ReferralRequest reloaded = app.referrals.find(r.id()).orElseThrow();
            equal(reloaded.messages(), ReferralRequest.fromJson(reloaded.toJson()).messages(), "messages persist");
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void matchScoreRewardsSkillsExperienceAndPreferences() {
        CandidateProfile fresher = Fixtures.completeProfile("Asha", "a@example.com").withYears(0);
        JobPosting javaEntry = withReq(Fixtures.job("g:a", "A", "1", "Java Developer"), 0, List.of("Java", "SQL", "Spring"));
        JobPosting goSenior = withReq(Fixtures.job("g:a", "A", "2", "Senior Go Engineer"), 6, List.of("Go", "Kubernetes"));
        JobMatcher.Match good = JobMatcher.match(javaEntry, fresher, null);
        JobMatcher.Match poor = JobMatcher.match(goSenior, fresher, null);
        check(good.score() > poor.score() + 30, good.score() + " vs " + poor.score());
        equal(List.of("Java", "SQL"), good.matched());
        check(!poor.experienceFits(), "6 years is above a fresher");
        check(poor.missing().contains("Go"), "missing skills listed");

        UserPrefs wantsData = UserPrefs.fresh(Instant.now()).withPreferences(EnumSet.of(JobCategory.DATA_SCIENTIST),
                Set.of("Pune"));
        check(JobMatcher.match(javaEntry, fresher, wantsData).score() < good.score(),
                "a role and city the seeker didn't pick count against it");
    }

    @Test
    void pitchDraftUsesOnlyTheProfileAndCsvIsSpreadsheetSafe() {
        CandidateProfile p = new CandidateProfile("Asha", "a@example.com", "", "", "", "r", "B.Tech CSE 2026",
                "2 internships", "Java, SQL, React", 0);
        JobPosting job = withReq(Fixtures.job("g:a", "Acme", "1", "Backend Engineer"), 0, List.of("Java", "Kafka", "SQL"));
        String pitch = PitchWriter.draft(p, job);
        check(pitch.contains("Backend Engineer") && pitch.contains("Acme"), pitch);
        check(pitch.contains("Java and SQL"), "names the overlapping skills: " + pitch);
        check(!pitch.contains("Kafka"), "never claims a skill the seeker didn't list");
        check(pitch.length() >= ReferralService.MIN_PITCH_LENGTH, "long enough to send");

        JobPosting tricky = Fixtures.job("g:a", "=HYPERLINK(\"x\")", "2", "SDE, \"Platform\"");
        String csv = CsvExport.jobs(List.of(tricky));
        check(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""), "formula neutralised and quoted: " + csv);
        check(csv.contains("\"SDE, \"\"Platform\"\"\""), "commas and quotes escaped");
        check(csv.startsWith("Posted,Date known,Company,Role"), "header row");
    }

    @Test
    void prefsTrackVisitsHiddenJobsAndSettings() {
        try {
            Account s = seeker();
            UserPrefs first = app.prefs.startVisit(s.id());
            clock.advance(Duration.ofDays(1));
            UserPrefs second = app.prefs.startVisit(s.id());
            equal(first.visitAt(), second.previousVisitAt(), "the last session marks what's new");
            app.prefs.hide(s.id(), "x:1", true);
            check(app.prefs.of(s.id()).hiddenJobIds().contains("x:1"), "hidden");
            app.prefs.setPreferences(s.id(), EnumSet.of(JobCategory.AI_ML), Set.of("Bengaluru"));
            equal(Set.of(JobCategory.AI_ML), app.prefs.of(s.id()).preferredRoles());
            clock.advance(Duration.ofDays(1));
            app.prefs.startVisit(s.id());
            check(app.prefs.of(s.id()).hiddenJobIds().isEmpty(), "hidden ids of jobs that are gone are dropped");
            check(!app.prefs.darkMode() && app.prefs.autoScan(), "defaults: light, auto-scan on");
            app.prefs.setDarkMode(true);
            app.prefs.setAutoScan(false);
            AppServices reopened = new AppServices(dir, (m, u, b) -> "{}", clock);
            check(reopened.prefs.darkMode() && !reopened.prefs.autoScan(), "settings persist");
            equal(Set.of("Bengaluru"), reopened.prefs.of(s.id()).preferredCities());
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void profileYearsReachThePacket() {
        CandidateProfile p = Fixtures.completeProfile("Asha", "a@example.com").withYears(2);
        equal(p, CandidateProfile.fromJson(p.toJson()));
        equal(-1, CandidateProfile.fromJson(Fixtures.completeProfile("A", "a@example.com").toJson()).years());
        JobPosting job = withReq(Fixtures.job("g:a", "Acme", "1", "Backend Engineer"), 3, List.of());
        ReferralRequest r = new ReferralRequest("REF-1", Instant.now(), "s", "r", "Ravi, SDE at Acme", job, p,
                Fixtures.PITCH);
        String packet = ReferralService.packet(r);
        check(packet.contains("Total exp.: 2 years"), packet);
        check(packet.contains("Min. exp.:  3+ years (stated in the posting)"), packet);
        equal("Ravi", r.referrerName());
    }
}
