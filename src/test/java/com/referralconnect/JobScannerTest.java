package com.referralconnect;

import com.referralconnect.TestRunner.Test;
import com.referralconnect.model.Ats;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.model.JobCategory;
import com.referralconnect.model.JobPosting;
import com.referralconnect.scan.JobScanner;
import com.referralconnect.scan.RawPosting;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.referralconnect.TestRunner.check;
import static com.referralconnect.TestRunner.equal;

class JobScannerTest {

    private static final CompanyBoard ACME = new CompanyBoard("Acme", Ats.GREENHOUSE, "acme");
    private static final Instant CUTOFF = Instant.parse("2026-09-01T00:00:00Z");

    private static RawPosting raw(String id, String title, String location, String postedAt) {
        return new RawPosting(id, title, List.of(location), postedAt == null ? null : Instant.parse(postedAt),
                "https://acme/" + id, "");
    }

    @Test
    void keepsOnlyRecentIndianTargetRoles() {
        List<JobPosting> jobs = JobScanner.extract(ACME, List.of(
                raw("1", "Software Engineer", "Bengaluru, India", "2026-09-20T00:00:00Z"),
                raw("2", "Software Engineer", "Austin, TX", "2026-09-20T00:00:00Z"),        // not India
                raw("3", "Software Engineer", "Pune", "2026-08-01T00:00:00Z"),              // too old
                raw("4", "Account Executive", "Mumbai", "2026-09-20T00:00:00Z"),            // not a target role
                raw("5", "Data Analyst Intern", "Remote - India", "2026-09-21T00:00:00Z"),
                raw("6", "Software Engineer", "Hyderabad", null)), CUTOFF);                 // no date
        equal(2, jobs.size());
        JobPosting sde = jobs.get(0);
        equal("greenhouse:acme:1", sde.id());
        equal("greenhouse:acme", sde.companyKey());
        equal("Acme", sde.company());
        equal("Bengaluru", sde.city());
        equal(JobCategory.SOFTWARE_DEVELOPER, sde.category());
        check(!sde.internship(), "full-time");
        JobPosting intern = jobs.get(1);
        equal(JobCategory.DATA_ANALYST, intern.category());
        check(intern.internship(), "internship detected");
    }

    @Test
    void showsOnlyTheIndianLocationsOfAMultiCountryPosting() {
        RawPosting multi = new RawPosting("9", "Forward Deployed Engineer",
                List.of("San Francisco", "Hyderabad, India", "India"), Instant.parse("2026-09-20T00:00:00Z"), "u", "");
        JobPosting job = JobScanner.extract(ACME, List.of(multi), CUTOFF).get(0);
        equal("Hyderabad, India", job.location());
        equal("Hyderabad", job.city());
    }

    @Test
    void scanCombinesBoardsSortsNewestFirstAndRecordsFailures() {
        Instant now = Instant.now();
        String older = now.minus(Duration.ofDays(5)).toString();
        String newer = now.minus(Duration.ofDays(1)).toString();
        String greenhouse = "{\"jobs\":[{\"id\":1,\"title\":\"Backend Engineer\",\"location\":{\"name\":\"Pune\"},"
                + "\"first_published\":\"" + older + "\",\"absolute_url\":\"u1\"}]}";
        String lever = "[{\"id\":\"x\",\"text\":\"Data Scientist\",\"createdAt\":"
                + Instant.parse(newer).toEpochMilli() + ",\"hostedUrl\":\"u2\",\"categories\":{\"location\":\"Bengaluru\"}}]";
        List<String> requested = Collections.synchronizedList(new ArrayList<>());
        JobScanner scanner = new JobScanner((method, url, body) -> {
            requested.add(url);
            if (url.contains("greenhouse")) {
                return greenhouse;
            }
            if (url.contains("lever")) {
                return lever;
            }
            throw new IOException("board not found (HTTP 404)");
        });
        List<String> progress = Collections.synchronizedList(new ArrayList<>());
        JobScanner.ScanReport report = scanner.scan(List.of(
                new CompanyBoard("GH Co", Ats.GREENHOUSE, "gh"),
                new CompanyBoard("Lever Co", Ats.LEVER, "lv"),
                new CompanyBoard("Gone Co", Ats.ASHBY, "gone")), Duration.ofDays(30), progress::add);

        equal(3, requested.size());
        equal(3, report.boardsScanned());
        equal(2, report.postingsSeen());
        equal(2, report.jobs().size());
        equal("Data Scientist", report.jobs().get(0).title(), "newest first");
        equal("Backend Engineer", report.jobs().get(1).title());
        equal(1, report.failures().size());
        check(report.failures().get("Gone Co").contains("404"), "failure reason kept");
        equal(3, progress.size());
    }

    @Test
    void jobsWithRealDatesSortBeforeFirstSeenOnes() {
        JobPosting old = Fixtures.job("greenhouse:a", "A", "1", "SDE");                    // posted 28 Sep
        JobPosting firstSeenToday = new JobPosting("radancy:b:2", "radancy:b", "B", "SDE", "Bengaluru", "Bengaluru",
                JobCategory.SOFTWARE_DEVELOPER, false, Instant.parse("2026-10-02T12:00:00Z"), "u", Ats.RADANCY, false);
        List<JobPosting> sorted = new ArrayList<>(List.of(firstSeenToday, old));
        sorted.sort(JobScanner.NEWEST_FIRST);
        equal(List.of(old, firstSeenToday), sorted, "a first-seen time doesn't outrank a real posting date");
    }

    @Test
    void brokenJsonFromOneBoardDoesNotStopTheScan() {
        JobScanner scanner = new JobScanner((method, url, body) -> url.contains("bad") ? "<html>oops</html>" : "{\"jobs\":[]}");
        JobScanner.ScanReport report = scanner.scan(List.of(
                new CompanyBoard("Bad", Ats.GREENHOUSE, "bad"),
                new CompanyBoard("Good", Ats.GREENHOUSE, "good")), Duration.ofDays(30), s -> { });
        equal(1, report.failures().size());
        check(report.failures().containsKey("Bad"), "bad board reported");
    }
}
