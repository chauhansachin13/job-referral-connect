package com.referralconnect;

import com.referralconnect.TestRunner.Test;
import com.referralconnect.model.Account;
import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.model.JobPosting;
import com.referralconnect.model.ReferralRequest;
import com.referralconnect.model.RequestStatus;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.CompanyDirectory;
import com.referralconnect.service.ReferralService;
import com.referralconnect.service.ServiceException;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.referralconnect.TestRunner.check;
import static com.referralconnect.TestRunner.equal;
import static com.referralconnect.TestRunner.fails;

class ReferralServiceTest {

    private final Path dir = Fixtures.tempDir();
    private final Fixtures.TestClock clock = new Fixtures.TestClock(Instant.parse("2026-10-01T09:00:00Z"));
    private final AppServices app = new AppServices(dir, url -> "{\"jobs\":[]}", clock);
    private final CompanyBoard mongo = board("MongoDB");
    private final CompanyBoard okta = board("Okta");
    private final JobPosting mongoJob = Fixtures.job(mongo.key(), "MongoDB", "1", "Software Engineer 3");

    private static CompanyBoard board(String name) {
        return CompanyDirectory.SEED.stream().filter(b -> b.name().equals(name)).findFirst().orElseThrow();
    }

    private Account seeker(String email) {
        Account a = app.auth.registerSeeker("Seeker " + email, email, "password1");
        return app.auth.updateProfile(a.id(), Fixtures.completeProfile("Seeker " + email, email));
    }

    private Account referrer(String email, CompanyBoard company) {
        clock.advance(Duration.ofMinutes(1));
        return app.auth.registerReferrer("Ref " + email, email, "password1", company, "SDE-2");
    }

    private ReferralRequest request(Account seeker, JobPosting job) {
        clock.advance(Duration.ofMinutes(1));
        return app.referrals.requestReferral(seeker.id(), job, seeker.profile(), Fixtures.PITCH);
    }

    @Test
    void routesToTheLeastBusyReferrerAtThatCompany() {
        try {
            Account first = referrer("r1@mongo.example", mongo);
            Account second = referrer("r2@mongo.example", mongo);
            referrer("r@okta.example", okta);
            Account s1 = seeker("s1@example.com");
            Account s2 = seeker("s2@example.com");
            Account s3 = seeker("s3@example.com");

            equal(first.id(), request(s1, mongoJob).referrerId(), "tie goes to the earliest referrer");
            equal(second.id(), request(s2, mongoJob).referrerId(), "then the one with fewer open requests");
            equal(first.id(), request(s3, mongoJob).referrerId(), "back to an even split");
            equal(2, app.referrals.referrerCountsByCompany().get(mongo.key()));
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void theReferrerReceivesTheFullPacket() {
        try {
            Account ref = referrer("r@mongo.example", mongo);
            Account s = seeker("asha@example.com");
            ReferralRequest sent = request(s, mongoJob);

            List<ReferralRequest> inbox = app.referrals.inbox(ref.id());
            equal(1, inbox.size());
            ReferralRequest got = inbox.get(0);
            equal(sent.id(), got.id());
            equal("Software Engineer 3", got.job().title());
            equal("https://drive.google.com/resume", got.candidate().resumeLink());
            equal(Fixtures.PITCH, got.pitch());
            equal(RequestStatus.PENDING, got.status());
            equal(1L, app.referrals.pendingCount(ref.id()));

            String packet = ReferralService.packet(got);
            for (String expected : List.of(sent.id(), "Software Engineer 3", "MongoDB", "asha@example.com",
                    "https://drive.google.com/resume", "Java, SQL", Fixtures.PITCH, mongoJob.url())) {
                check(packet.contains(expected), "packet should contain " + expected + "\n" + packet);
            }
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void requiresAReadyProfileAndAPitch() {
        try {
            referrer("r@mongo.example", mongo);
            Account s = app.auth.registerSeeker("Bare", "bare@example.com", "password1");
            String missing = fails(ServiceException.class, () -> app.referrals.requestReferral(s.id(), mongoJob,
                    s.profile(), Fixtures.PITCH)).getMessage();
            check(missing.contains("resume link") && missing.contains("skills"), missing);
            CandidateProfile ready = Fixtures.completeProfile("Bare", "bare@example.com");
            fails(ServiceException.class, () -> app.referrals.requestReferral(s.id(), mongoJob, ready, "pls refer"));
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void blocksDuplicatesAndFloodingOneCompany() {
        try {
            referrer("r@mongo.example", mongo);
            Account s = seeker("s@example.com");
            request(s, mongoJob);
            check(fails(ServiceException.class, () -> request(s, mongoJob)).getMessage().contains("already"),
                    "duplicate blocked");
            request(s, Fixtures.job(mongo.key(), "MongoDB", "2", "Backend Engineer"));
            request(s, Fixtures.job(mongo.key(), "MongoDB", "3", "Data Engineer"));
            check(fails(ServiceException.class, () -> request(s, Fixtures.job(mongo.key(), "MongoDB", "4", "SRE")))
                    .getMessage().contains("open requests"), "cap of " + ReferralService.MAX_OPEN_PER_COMPANY);
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void needsAnAcceptingReferrerAtThatCompany() {
        try {
            Account s = seeker("s@example.com");
            referrer("r@okta.example", okta);
            check(fails(ServiceException.class, () -> request(s, mongoJob)).getMessage().contains("No referrer"),
                    "no MongoDB referrer");
            Account paused = referrer("r@mongo.example", mongo);
            app.auth.updateReferrerSettings(paused.id(), "SDE", false);
            fails(ServiceException.class, () -> request(s, mongoJob));
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void referrerMarksReferredAndSeekerSeesIt() {
        try {
            Account ref = referrer("r@mongo.example", mongo);
            Account s = seeker("s@example.com");
            ReferralRequest r = request(s, mongoJob);
            app.referrals.markViewed(ref.id(), r.id());
            app.referrals.respond(ref.id(), r.id(), RequestStatus.REFERRED, "Submitted in Workday");

            ReferralRequest seen = app.referrals.forSeeker(s.id()).get(0);
            equal(RequestStatus.REFERRED, seen.status());
            equal("Submitted in Workday", seen.referrerNote());
            check(seen.viewedAt() != null, "viewed recorded");
            equal(3, seen.timeline().size(), "sent, opened, referred");
            equal(RequestStatus.REFERRED, app.referrals.latestStatusByJob(s.id()).get(mongoJob.id()));
            fails(ServiceException.class, () -> app.referrals.respond(ref.id(), r.id(), RequestStatus.DECLINED, "x"));
            fails(ServiceException.class, () -> app.referrals.withdraw(s.id(), r.id()));
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void needsInfoThenResubmitGoesBackToPending() {
        try {
            Account ref = referrer("r@mongo.example", mongo);
            Account s = seeker("s@example.com");
            ReferralRequest r = request(s, mongoJob);
            fails(ServiceException.class, () -> app.referrals.respond(ref.id(), r.id(), RequestStatus.NEEDS_INFO, " "));
            app.referrals.respond(ref.id(), r.id(), RequestStatus.NEEDS_INFO, "Share a resume with project links");
            equal(0L, app.referrals.pendingCount(ref.id()));

            CandidateProfile better = new CandidateProfile("Seeker s", "s@example.com", "", "", "",
                    "https://drive.google.com/resume-v2", "B.Tech", "Fresher", "Java, SQL, Kafka");
            app.referrals.resubmit(s.id(), r.id(), better, Fixtures.PITCH + " Added project links.");
            ReferralRequest again = app.referrals.find(r.id()).orElseThrow();
            equal(RequestStatus.PENDING, again.status());
            equal("https://drive.google.com/resume-v2", again.candidate().resumeLink());
            equal(1L, app.referrals.pendingCount(ref.id()));
            fails(ServiceException.class, () -> app.referrals.resubmit(s.id(), r.id(), better, Fixtures.PITCH));
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void onlyTheOwnersCanActOnARequest() {
        try {
            Account ref = referrer("r1@mongo.example", mongo);
            Account otherRef = referrer("r2@mongo.example", mongo);
            Account s = seeker("s@example.com");
            Account stranger = seeker("x@example.com");
            ReferralRequest r = request(s, mongoJob);
            equal(ref.id(), r.referrerId());
            fails(ServiceException.class, () -> app.referrals.respond(otherRef.id(), r.id(), RequestStatus.REFERRED, ""));
            fails(ServiceException.class, () -> app.referrals.withdraw(stranger.id(), r.id()));
            app.referrals.withdraw(s.id(), r.id());
            fails(ServiceException.class, () -> app.referrals.respond(ref.id(), r.id(), RequestStatus.REFERRED, ""));
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void afterADeclineTheNextRequestGoesToSomeoneElse() {
        try {
            Account first = referrer("r1@mongo.example", mongo);
            Account second = referrer("r2@mongo.example", mongo);
            Account s = seeker("s@example.com");
            ReferralRequest r = request(s, mongoJob);
            equal(first.id(), r.referrerId());
            app.referrals.respond(first.id(), r.id(), RequestStatus.DECLINED, "Need 2+ years for this team");
            ReferralRequest retry = request(s, mongoJob);
            equal(second.id(), retry.referrerId());
            app.referrals.respond(second.id(), retry.id(), RequestStatus.DECLINED, "Same here");
            check(fails(ServiceException.class, () -> request(s, mongoJob)).getMessage().contains("No referrer"),
                    "nobody left who has not declined");
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void inboxListsWaitingRequestsFirstOldestOnTop() {
        try {
            Account ref = referrer("r@mongo.example", mongo);
            Account a = seeker("a@example.com");
            Account b = seeker("b@example.com");
            Account c = seeker("c@example.com");
            ReferralRequest ra = request(a, mongoJob);
            ReferralRequest rb = request(b, mongoJob);
            ReferralRequest rc = request(c, mongoJob);
            clock.advance(Duration.ofMinutes(5));
            app.referrals.respond(ref.id(), ra.id(), RequestStatus.REFERRED, "");
            List<String> order = app.referrals.inbox(ref.id()).stream().map(ReferralRequest::id).toList();
            equal(List.of(rb.id(), rc.id(), ra.id()), order);
        } finally {
            Fixtures.delete(dir);
        }
    }
}
