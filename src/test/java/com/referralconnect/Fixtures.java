package com.referralconnect;

import com.referralconnect.model.Ats;
import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.JobCategory;
import com.referralconnect.model.JobPosting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.stream.Stream;

/** Shared test helpers: temp folders, a controllable clock and sample data. */
final class Fixtures {

    private Fixtures() {
    }

    static Path tempDir() {
        try {
            Path dir = Files.createTempDirectory("jrc-test");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void delete(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // Best effort; the OS cleans its temp folder anyway.
        }
    }

    /** A clock the test moves forward by hand. */
    static final class TestClock extends Clock {
        private Instant now;

        TestClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static JobPosting job(String companyKey, String company, String atsId, String title) {
        return new JobPosting(companyKey + ":" + atsId, companyKey, company, title, "Bengaluru, India", "Bengaluru",
                JobCategory.SOFTWARE_DEVELOPER, false, Instant.parse("2026-09-28T10:00:00Z"),
                "https://example.com/jobs/" + atsId, Ats.GREENHOUSE);
    }

    static CandidateProfile completeProfile(String name, String email) {
        return new CandidateProfile(name, email, "+91 98765 43210", "https://linkedin.com/in/x",
                "https://github.com/x", "https://drive.google.com/resume", "B.Tech CSE 2026", "Fresher",
                "Java, SQL");
    }

    static final String PITCH = "Built a Spring Boot payments service in my internship; keen on this team.";
}
