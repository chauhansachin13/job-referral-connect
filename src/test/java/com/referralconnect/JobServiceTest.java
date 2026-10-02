package com.referralconnect;

import com.referralconnect.TestRunner.Test;
import com.referralconnect.model.Ats;
import com.referralconnect.model.JobCategory;
import com.referralconnect.model.JobPosting;
import com.referralconnect.service.AppServices;
import com.referralconnect.service.JobService;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.referralconnect.TestRunner.check;
import static com.referralconnect.TestRunner.equal;

class JobServiceTest {

    private static JobPosting undated(String id, Instant seen) {
        return new JobPosting(id, "radancy:x", "X", "Software Engineer", "Bengaluru, India", "Bengaluru",
                JobCategory.SOFTWARE_DEVELOPER, false, seen, "u", Ats.RADANCY,
                false);
    }

    @Test
    void undatedJobsKeepTheTimeTheyWereFirstSeen() {
        Instant now = Instant.parse("2026-10-02T12:00:00Z");
        Instant cutoff = now.minus(Duration.ofDays(30));
        JobPosting dated = Fixtures.job("greenhouse:okta", "Okta", "1", "SDE");
        List<JobPosting> previous = List.of(undated("a", now.minus(Duration.ofDays(5))),
                undated("old", now.minus(Duration.ofDays(40))));
        List<JobPosting> fresh = List.of(undated("a", now), undated("b", now), undated("old", now), dated);

        List<JobPosting> merged = JobService.keepFirstSeen(fresh, previous, cutoff);
        Map<String, Instant> byId = new HashMap<>();
        merged.forEach(j -> byId.put(j.id(), j.postedAt()));
        equal(now.minus(Duration.ofDays(5)), byId.get("a"), "seen before: keeps the first-seen time");
        equal(now, byId.get("b"), "new today");
        check(!byId.containsKey("old"), "first seen more than 30 days ago: no longer recent");
        equal(dated.postedAt(), byId.get(dated.id()), "dated jobs untouched");
    }

    @Test
    void aCompanyThatFailsKeepsItsLastOpeningsAndOthersAreReplaced() {
        Path dir = Fixtures.tempDir();
        try {
            String recent = Instant.now().minus(Duration.ofDays(2)).toString();
            String oktaBoard = "{\"jobs\":[{\"id\":77,\"title\":\"Software Engineer\",\"location\":{\"name\":\"Bengaluru, India\"},"
                    + "\"first_published\":\"" + recent + "\",\"absolute_url\":\"https://okta/77\"}]}";
            AppServices app = new AppServices(dir, (method, url, body) -> {
                if (url.equals("https://boards-api.greenhouse.io/v1/boards/okta/jobs")) {
                    return oktaBoard;
                }
                throw new IOException("offline"); // every other company, Stripe included, is unreachable
            }, java.time.Clock.systemUTC());

            JobPosting oldStripe = Fixtures.job("greenhouse:stripe", "Stripe", "s1", "Backend Engineer");
            JobPosting staleOkta = Fixtures.job("greenhouse:okta", "Okta", "gone", "Closed Role");
            JobPosting recentStripe = new JobPosting(oldStripe.id(), oldStripe.companyKey(), oldStripe.company(),
                    oldStripe.title(), oldStripe.location(), oldStripe.city(), oldStripe.category(), false,
                    Instant.now().minus(Duration.ofDays(3)), oldStripe.url(), oldStripe.source());
            app.store.update(s -> s.jobs.addAll(List.of(recentStripe, staleOkta)));

            app.jobs.scanNow(line -> { });

            List<String> ids = app.jobs.jobs().stream().map(JobPosting::id).toList();
            check(ids.contains("greenhouse:okta:77"), "Okta's fresh opening is in: " + ids);
            check(!ids.contains(staleOkta.id()), "Okta's closed opening is gone");
            check(ids.contains(recentStripe.id()), "Stripe could not be read, so its last openings stay");
            check(app.jobs.lastScanFailures().containsKey("Stripe"), "Stripe's failure is reported");
            equal(app.directory.all().size(), app.jobs.lastScanBoards());
        } finally {
            Fixtures.delete(dir);
        }
    }
}
