package com.referralconnect;

import com.referralconnect.TestRunner.Test;
import com.referralconnect.model.Account;
import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.model.Role;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.CompanyDirectory;
import com.referralconnect.service.ServiceException;

import java.nio.file.Path;
import java.time.Instant;

import static com.referralconnect.TestRunner.check;
import static com.referralconnect.TestRunner.equal;
import static com.referralconnect.TestRunner.fails;

class AuthServiceTest {

    private final Path dir = Fixtures.tempDir();
    private final AppServices app = new AppServices(dir, (method, url, body) -> "{\"jobs\":[]}",
            new Fixtures.TestClock(Instant.parse("2026-10-01T09:00:00Z")));
    private final CompanyBoard mongo = CompanyDirectory.SEED.stream()
            .filter(b -> b.name().equals("MongoDB")).findFirst().orElseThrow();

    @Test
    void registersAndSignsInBothRoles() {
        try {
            Account seeker = app.auth.registerSeeker("Asha Rao", "Asha@Example.com ", "password1");
            equal(Role.SEEKER, seeker.role());
            equal("asha@example.com", seeker.email());
            Account referrer = app.auth.registerReferrer("Vikram", "vikram@example.com", "password2", mongo, "SDE-2");
            equal(mongo.key(), referrer.companyKey());
            equal("Vikram, SDE-2 at MongoDB", referrer.referrerTitle());
            equal(seeker.id(), app.auth.login("ASHA@example.com", "password1").id());
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void rejectsBadRegistrations() {
        try {
            app.auth.registerSeeker("Asha", "asha@example.com", "password1");
            check(fails(ServiceException.class, () -> app.auth.registerSeeker("Other", "ASHA@example.com", "password9"))
                    .getMessage().contains("already exists"), "duplicate email");
            fails(ServiceException.class, () -> app.auth.registerSeeker("X", "not-an-email", "password1"));
            fails(ServiceException.class, () -> app.auth.registerSeeker("X", "x@example.com", "short"));
            fails(ServiceException.class, () -> app.auth.registerSeeker(" ", "y@example.com", "password1"));
            fails(ServiceException.class, () -> app.auth.registerReferrer("R", "r@example.com", "password1", null, ""));
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameMessage() {
        try {
            app.auth.registerSeeker("Asha", "asha@example.com", "password1");
            String wrong = fails(ServiceException.class, () -> app.auth.login("asha@example.com", "nope12345")).getMessage();
            String unknown = fails(ServiceException.class, () -> app.auth.login("who@example.com", "nope12345")).getMessage();
            equal(wrong, unknown);
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void profileUpdatesKeepEmailsUnique() {
        try {
            Account a = app.auth.registerSeeker("A", "a@example.com", "password1");
            app.auth.registerSeeker("B", "b@example.com", "password1");
            Account updated = app.auth.updateProfile(a.id(), Fixtures.completeProfile("A New", "a2@example.com"));
            equal("a2@example.com", updated.email());
            equal(a.id(), app.auth.login("a2@example.com", "password1").id());
            fails(ServiceException.class, () -> app.auth.updateProfile(a.id(), CandidateProfile.of("A", "b@example.com")));
        } finally {
            Fixtures.delete(dir);
        }
    }

    @Test
    void referrerCanPauseRequests() {
        try {
            Account r = app.auth.registerReferrer("R", "r@example.com", "password1", mongo, "SDE");
            app.auth.updateReferrerSettings(r.id(), "Senior SDE", false);
            Account reloaded = app.auth.require(r.id());
            equal("Senior SDE", reloaded.designation());
            check(!reloaded.acceptingRequests(), "paused");
            check(app.referrals.referrersFor(mongo.key()).isEmpty(), "paused referrer not offered");
        } finally {
            Fixtures.delete(dir);
        }
    }
}
