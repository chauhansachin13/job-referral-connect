package com.referralconnect;

import com.referralconnect.TestRunner.Test;
import com.referralconnect.model.Ats;
import com.referralconnect.scan.AtsParsers;
import com.referralconnect.scan.RawPosting;

import java.time.Instant;
import java.util.List;

import static com.referralconnect.TestRunner.check;
import static com.referralconnect.TestRunner.equal;

/** Fixtures mirror the real public API responses (trimmed to the fields we read). */
class AtsParsersTest {

    static final String GREENHOUSE = """
            {"jobs":[
              {"id":8172510,"title":" Software Engineer ","location":{"name":"Bengaluru, India"},
               "updated_at":"2026-09-25T16:45:00-04:00","first_published":"2026-09-20T10:00:00+05:30",
               "absolute_url":"https://stripe.com/jobs/search?gh_jid=8172510"},
              {"id":2,"title":"Data Analyst","location":{"name":"Pune"},
               "updated_at":"2026-09-26T00:00:00Z","absolute_url":"https://x/2"}
            ],"meta":{"total":2}}""";

    static final String LEVER = """
            [{"id":"6ed76ce8","text":"Data Scientist","createdAt":1790000000000,
              "hostedUrl":"https://jobs.lever.co/acme/6ed76ce8","country":"IN",
              "categories":{"commitment":"Intern","location":"Koramangala","allLocations":["Koramangala"]}}]""";

    static final String ASHBY = """
            {"jobs":[
              {"id":"a1","title":"Forward Deployed Engineer","location":"San Francisco",
               "secondaryLocations":[{"location":"Hyderabad, India"}],
               "employmentType":"FullTime","publishedAt":"2026-09-24T14:44:49.699+00:00",
               "jobUrl":"https://jobs.ashbyhq.com/acme/a1","isListed":true},
              {"id":"hidden","title":"Unlisted","location":"Bengaluru","publishedAt":"2026-09-24T00:00:00Z",
               "jobUrl":"https://x","isListed":false}
            ]}""";

    @Test
    void greenhouseUsesFirstPublishedThenUpdatedAt() {
        List<RawPosting> jobs = AtsParsers.parse(Ats.GREENHOUSE, GREENHOUSE);
        equal(2, jobs.size());
        RawPosting first = jobs.get(0);
        equal("8172510", first.atsId());
        equal("Software Engineer", first.title());
        equal(List.of("Bengaluru, India"), first.locations());
        equal(Instant.parse("2026-09-20T04:30:00Z"), first.postedAt());
        equal("https://stripe.com/jobs/search?gh_jid=8172510", first.url());
        equal(Instant.parse("2026-09-26T00:00:00Z"), jobs.get(1).postedAt());
    }

    @Test
    void leverReadsCommitmentAndCountryCode() {
        RawPosting job = AtsParsers.parse(Ats.LEVER, LEVER).get(0);
        equal("Data Scientist", job.title());
        equal(Instant.ofEpochMilli(1790000000000L), job.postedAt());
        equal("Intern", job.employmentHint());
        check(job.locations().contains("India"), "country IN becomes an India location: " + job.locations());
    }

    @Test
    void ashbySkipsUnlistedAndReadsSecondaryLocations() {
        List<RawPosting> jobs = AtsParsers.parse(Ats.ASHBY, ASHBY);
        equal(1, jobs.size());
        check(jobs.get(0).locations().contains("Hyderabad, India"), "secondary location kept");
        equal(Instant.parse("2026-09-24T14:44:49.699Z"), jobs.get(0).postedAt());
    }

    @Test
    void unparseableDatesBecomeNull() {
        String body = "{\"jobs\":[{\"id\":1,\"title\":\"SDE\",\"location\":{\"name\":\"Pune\"},"
                + "\"first_published\":\"yesterday\",\"absolute_url\":\"u\"}]}";
        equal(null, AtsParsers.parse(Ats.GREENHOUSE, body).get(0).postedAt());
    }
}
