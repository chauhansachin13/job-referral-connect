package com.referralconnect;

import com.referralconnect.TestRunner.Test;
import com.referralconnect.json.Json;
import com.referralconnect.model.Ats;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.model.JobCategory;
import com.referralconnect.model.JobPosting;
import com.referralconnect.scan.Http;
import com.referralconnect.scan.JobScanner;
import com.referralconnect.scan.RawPosting;
import com.referralconnect.scan.source.BoardSource;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.referralconnect.TestRunner.check;
import static com.referralconnect.TestRunner.equal;
import static com.referralconnect.TestRunner.fails;

/** Each careers platform adapter against canned responses shaped like the real ones. */
class SourcesTest {

    static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    static final Instant CUTOFF = NOW.minus(Duration.ofDays(30));

    /** Records every request and answers from a handler. */
    static final class FakeHttp implements Http {
        interface Handler {
            String answer(String method, String url, String body) throws IOException;
        }

        final List<String> calls = Collections.synchronizedList(new ArrayList<>());
        private final Handler handler;

        FakeHttp(Handler handler) {
            this.handler = handler;
        }

        @Override
        public String send(String method, String url, String body) throws IOException {
            calls.add(method + " " + url + (body == null ? "" : " " + body));
            return handler.answer(method, url, body);
        }
    }

    static List<RawPosting> fetch(CompanyBoard board, Http http) throws Exception {
        Instant noRush = Instant.now().plus(Duration.ofHours(1));
        return BoardSource.of(board.ats()).fetch(board, new BoardSource.Context(http, NOW, CUTOFF, noRush));
    }

    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    static int offsetOf(String body) {
        Matcher m = Pattern.compile("\"offset\":(\\d+)").matcher(body);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    // ---------------------------------------------------------------- Workday

    static final CompanyBoard WORKDAY = new CompanyBoard("Acme", Ats.WORKDAY, "acme/wd5/External");

    @Test
    void workdayUsesTheIndiaCountryFacetAndReadsEveryPage() throws Exception {
        String facets = Json.writeCompact(obj("total", 900, "jobPostings", List.of(), "facets", List.of(
                obj("facetParameter", "locationMainGroup", "values", List.of(
                        obj("facetParameter", "locationCountry", "descriptor", "Country", "values", List.of(
                                obj("descriptor", "United States of America", "id", "US", "count", 855),
                                obj("descriptor", "India", "id", "IN", "count", 45))))),
                obj("facetParameter", "jobFamilyGroup", "values", List.of(
                        obj("descriptor", "Engineering", "id", "ENG", "count", 300))))));
        String[] postedOn = {"Posted Today", "Posted Yesterday", "Posted 3 Days Ago", "Posted 30+ Days Ago"};
        FakeHttp http = new FakeHttp((method, url, body) -> {
            check(url.equals("https://acme.wd5.myworkdayjobs.com/wday/cxs/acme/External/jobs"), url);
            if (body.contains("\"limit\":1,")) {
                return facets;
            }
            check(body.contains("\"appliedFacets\":{\"locationCountry\":[\"IN\"]}"), "India filter applied: " + body);
            int offset = offsetOf(body);
            List<Object> postings = new ArrayList<>();
            for (int n = offset; n < Math.min(offset + 20, 45); n++) {
                postings.add(obj("title", "Software Engineer " + n,
                        "externalPath", "/job/India-Bengaluru/Software-Engineer_JR" + n,
                        "locationsText", "2 Locations",
                        "postedOn", n < postedOn.length ? postedOn[n] : "Posted 2 Days Ago",
                        "bulletFields", List.of("JR" + n)));
            }
            // Like the real thing, only the first page reports the total.
            return Json.writeCompact(obj("total", offset == 0 ? 45 : 0, "jobPostings", postings));
        });

        List<RawPosting> raw = fetch(WORKDAY, http);
        equal(45, raw.size());
        equal(4, http.calls.size(), "facet lookup + 3 pages");
        // Workday only gives the day, so dates are pinned to the start of that day.
        equal(Instant.parse("2026-10-02T00:00:00Z"), raw.get(0).postedAt(), "today");
        equal(Instant.parse("2026-10-01T00:00:00Z"), raw.get(1).postedAt(), "yesterday");
        equal(Instant.parse("2026-09-29T00:00:00Z"), raw.get(2).postedAt(), "3 days ago");
        equal(Instant.parse("2026-09-01T00:00:00Z"), raw.get(3).postedAt(), "30+ days is older than the window");
        equal("https://acme.wd5.myworkdayjobs.com/en-US/External/job/India-Bengaluru/Software-Engineer_JR0",
                raw.get(0).url());

        List<JobPosting> jobs = JobScanner.extract(WORKDAY, raw, CUTOFF);
        equal(44, jobs.size(), "the 30+ day posting is dropped");
        equal("Bengaluru", jobs.get(0).city());
        equal(JobCategory.SOFTWARE_DEVELOPER, jobs.get(0).category());
    }

    @Test
    void workdayFallsBackToIndianCityValues() throws Exception {
        String facets = Json.writeCompact(obj("facets", List.of(obj("facetParameter", "locations", "values", List.of(
                obj("descriptor", "Bangalore, India", "id", "BLR"),
                obj("descriptor", "Hyderabad, India", "id", "HYD"),
                obj("descriptor", "Austin, TX", "id", "AUS"),
                obj("descriptor", "Indianapolis, IN", "id", "INDY"))))));
        FakeHttp http = new FakeHttp((method, url, body) -> {
            if (body.contains("\"limit\":1,")) {
                return facets;
            }
            check(body.contains("\"locations\":[\"BLR\",\"HYD\"]"), "only Indian cities selected: " + body);
            return Json.writeCompact(obj("total", 1, "jobPostings", List.of(obj("title", "Data Analyst",
                    "externalPath", "/job/Hyderabad/Data-Analyst_R1", "locationsText", "Hyderabad, India",
                    "postedOn", "Posted 5 Days Ago"))));
        });
        List<RawPosting> raw = fetch(WORKDAY, http);
        equal(1, raw.size());
        equal("Hyderabad", JobScanner.extract(WORKDAY, raw, CUTOFF).get(0).city());
    }

    @Test
    void workdayWithoutIndiaIsReportedNotGuessed() {
        FakeHttp http = new FakeHttp((m, u, b) -> Json.writeCompact(obj("facets", List.of(obj(
                "facetParameter", "locationCountry", "values", List.of(obj("descriptor", "Germany", "id", "DE")))))));
        check(fails(IOException.class, () -> fetch(WORKDAY, http)).getMessage().contains("no India"), "message");
    }

    @Test
    void workdayOnTheSharedMyworkdaysiteHost() throws Exception {
        CompanyBoard microchip = new CompanyBoard("Microchip", Ats.WORKDAY, "microchiphr/wd5/External/myworkdaysite");
        FakeHttp http = new FakeHttp((method, url, body) -> {
            check(url.equals("https://wd5.myworkdaysite.com/wday/cxs/microchiphr/External/jobs"), url);
            if (body.contains("\"limit\":1,")) {
                return Json.writeCompact(obj("facets", List.of(obj("facetParameter", "locationCountry",
                        "values", List.of(obj("descriptor", "India", "id", "IN"))))));
            }
            return Json.writeCompact(obj("total", 1, "jobPostings", List.of(obj("title", "Embedded Software Engineer",
                    "externalPath", "/job/Bangalore/Embedded-SW_R1", "locationsText", "Bangalore, India",
                    "postedOn", "Posted Today"))));
        });
        List<RawPosting> raw = fetch(microchip, http);
        equal("https://wd5.myworkdaysite.com/en-US/recruiting/microchiphr/External/job/Bangalore/Embedded-SW_R1",
                raw.get(0).url());
    }

    // ---------------------------------------------------------------- Jibe, IBM, Radancy

    @Test
    void jibeReadsAmdStyleJobs() throws Exception {
        CompanyBoard amd = new CompanyBoard("AMD", Ats.JIBE, "careers.amd.com");
        FakeHttp http = new FakeHttp((m, url, b) -> {
            check(url.startsWith("https://careers.amd.com/api/jobs?location=India&page=1&sortBy=posted_date"), url);
            return Json.writeCompact(obj("totalCount", 2, "jobs", List.of(
                    obj("data", obj("slug", "87964", "title", "Software Development Engineer", "city", "Bangalore",
                            "state", "Karnataka", "country", "India", "posted_date", "2026-10-02T08:54:00+0000",
                            "employment_type", "FULL_TIME")),
                    obj("data", obj("slug", "87965", "title", "Silicon Design Intern", "city", "Hyderabad",
                            "state", "Telangana", "country", "India", "posted_date", "2026-09-30T00:00:00+0000",
                            "employment_type", "INTERN")))));
        });
        List<RawPosting> raw = fetch(amd, http);
        equal(2, raw.size());
        equal(1, http.calls.size());
        equal(Instant.parse("2026-10-02T08:54:00Z"), raw.get(0).postedAt());
        equal("https://careers.amd.com/careers-home/jobs/87964", raw.get(0).url());
        equal("Bengaluru", JobScanner.extract(amd, raw, CUTOFF).get(0).city());
    }

    @Test
    void ibmQueriesIndiaAndReadsLevels() throws Exception {
        CompanyBoard ibm = new CompanyBoard("IBM", Ats.IBM, "ibm");
        FakeHttp http = new FakeHttp((m, url, body) -> {
            check(m.equals("POST") && url.equals("https://www-api.ibm.com/search/api/v2"), url);
            check(body.contains("\"field_keyword_05\":\"India\"") && body.contains("\"dcdate\":\"desc\""), body);
            return Json.writeCompact(obj("hits", obj("total", obj("value", 2), "hits", List.of(
                    obj("_id", "a1", "_source", obj("title", "Data Engineer-Data Platforms", "dcdate", "2026-10-02",
                            "url", "https://careers.ibm.com/careers/JobDetail?jobId=132208",
                            "field_keyword_19", "Hyderabad, IN", "field_keyword_18", "Professional")),
                    obj("_id", "a2", "_source", obj("title", "Software Developer Intern", "dcdate", "2026-09-29",
                            "url", "https://careers.ibm.com/careers/JobDetail?jobId=132209",
                            "field_keyword_19", "Bangalore, IN", "field_keyword_18", "Internship"))))));
        });
        List<RawPosting> raw = fetch(ibm, http);
        List<JobPosting> jobs = JobScanner.extract(ibm, raw, CUTOFF);
        equal(2, jobs.size());
        equal(JobCategory.DATA_ENGINEER, jobs.get(0).category());
        equal("Hyderabad", jobs.get(0).city());
        check(jobs.get(1).internship(), "IBM internship level");
        equal(Instant.parse("2026-10-02T00:00:00Z"), jobs.get(0).postedAt());
    }

    @Test
    void radancyParsesTheResultsFragmentAndFlagsMissingDates() throws Exception {
        CompanyBoard synopsys = new CompanyBoard("Synopsys", Ats.RADANCY, "careers.example.com");
        String html = "<section data-total-results=\"2\"><ul>"
                + "<li class=\"item\"><a class=\"sr-job-link\" href=\"/job/bengaluru/sw/44408/94524277376\">"
                + "<h2>Software Engineer &amp; Tools &#x2B;&#x2B;<img src=\"x.svg\"></h2><div class=\"sr-wrapper\">"
                + "<span class=\"job-location\"><img src=\"pin.png\">Bengaluru, India</span></div></a></li>"
                + "<li><a href=\"/job/hyderabad/data/44408/111\"><h2>Data Analyst</h2>"
                + "<span class=\"job-location\">Hyderabad, India</span>"
                + "<span class=\"job-date-posted\">10/01/2026</span></a></li></ul></section>";
        FakeHttp http = new FakeHttp((m, url, b) -> {
            check(url.startsWith("https://careers.example.com/search-jobs/results?ActiveFacetID=1269750"), url);
            check(url.contains("FacetFilters%5B0%5D.ID=1269750"), "India GeoNames filter: " + url);
            return Json.writeCompact(obj("results", html));
        });
        List<RawPosting> raw = fetch(synopsys, http);
        equal(2, raw.size());
        equal("Software Engineer & Tools ++", raw.get(0).title());
        equal("https://careers.example.com/job/bengaluru/sw/44408/94524277376", raw.get(0).url());
        check(raw.get(0).undated() && raw.get(0).postedAt() == null, "no date on the page");
        equal(Instant.parse("2026-10-01T00:00:00Z"), raw.get(1).postedAt());
        check(!raw.get(1).undated(), "dated when the page shows a date");

        List<JobPosting> jobs = JobScanner.extract(synopsys, raw, CUTOFF, NOW);
        equal(2, jobs.size());
        check(!jobs.get(0).dateKnown(), "first-seen date is labelled as such");
        equal(NOW, jobs.get(0).postedAt());
        check(jobs.get(1).dateKnown(), "real date kept");
        equal("Bengaluru", jobs.get(0).city());
    }

    static List<RawPosting> radancy(String host, String html) throws Exception {
        return fetch(new CompanyBoard("Co", Ats.RADANCY, host),
                new FakeHttp((m, u, b) -> Json.writeCompact(obj("results", html))));
    }

    @Test
    void radancyVariantsWithoutHeadingsOrWithLocalePrefixes() throws Exception {
        // Arm-style: the title is the link text and the location class is plain "location".
        List<RawPosting> arm = radancy("careers.arm.com",
                "<ul id=\"search-results-jobs\" data-results-count=\"1\"><li class=\"job-card\">"
                        + "<a class=\"job-card__title\" href=\"/job/bengaluru/staff-devops-engineer/33099/83318969776\">"
                        + "Staff DevOps Engineer</a> <span class=\"location\">Bengaluru, India</span>"
                        + "<span class=\"category\">IT</span></li></ul>");
        equal(1, arm.size());
        equal("Staff DevOps Engineer", arm.get(0).title());
        check(arm.get(0).locations().contains("Bengaluru, India"), "location read: " + arm.get(0).locations());
        // Moody's-style: a locale in the link, and a date.
        List<RawPosting> moodys = radancy("careers.moodys.com",
                "<ul><li><a href=\"/en/job/bengaluru/dir-data-specialist/49841/101070714112\"><h2>Dir-Data Specialist</h2>"
                        + "<span class=\"job-location\">Bengaluru, Karnataka</span>"
                        + "<span class=\"job-date-posted\">09/24/2026</span></a></li></ul>");
        equal("https://careers.moodys.com/en/job/bengaluru/dir-data-specialist/49841/101070714112", moodys.get(0).url());
        equal(Instant.parse("2026-09-24T00:00:00Z"), moodys.get(0).postedAt());
        check(moodys.get(0).locations().contains("Bengaluru, Karnataka"), "location read");
    }

    // ---------------------------------------------------------------- SmartRecruiters

    static final CompanyBoard BOSCH = new CompanyBoard("Bosch", Ats.SMARTRECRUITERS, "BoschGroup");

    static Map<String, Object> srPosting(String id, String name, String released, String level) {
        return obj("id", id, "name", name, "releasedDate", released,
                "location", obj("city", "bengaluru", "country", "in", "fullLocation", "bengaluru, , India"),
                "typeOfEmployment", obj("label", "Full-time"), "experienceLevel", obj("label", level));
    }

    @Test
    void smartRecruitersMapsPostingsAndInternships() throws Exception {
        FakeHttp http = new FakeHttp((m, url, b) -> {
            check(url.startsWith("https://api.smartrecruiters.com/v1/companies/BoschGroup/postings?country=in"), url);
            return Json.writeCompact(obj("totalFound", 2, "content", List.of(
                    srPosting("1", "Software Developer", "2026-10-01T14:58:34.024Z", "Mid-Senior Level"),
                    srPosting("2", "Data Science Intern", "2026-09-30T10:00:00.000Z", "Internship"))));
        });
        List<RawPosting> raw = fetch(BOSCH, http);
        equal(2, raw.size());
        equal(1, http.calls.size());
        equal("https://jobs.smartrecruiters.com/BoschGroup/1", raw.get(0).url());
        List<JobPosting> jobs = JobScanner.extract(BOSCH, raw, CUTOFF);
        check(!jobs.get(0).internship() && jobs.get(1).internship(), "internship from experience level");
        equal("Bengaluru", jobs.get(0).city());
    }

    @Test
    void smartRecruitersStopsOnceResultsAreOld() throws Exception {
        List<Object> old = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            old.add(srPosting("o" + i, "Software Engineer", "2026-06-01T00:00:00Z", ""));
        }
        FakeHttp http = new FakeHttp((m, u, b) -> Json.writeCompact(obj("totalFound", 500, "content", old)));
        fetch(BOSCH, http);
        equal(1, http.calls.size(), "no further pages after an all-old page");
    }

    @Test
    void aCompanyOutOfTimeKeepsItsFirstPageAndStops() throws Exception {
        List<Object> recent = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            recent.add(srPosting("r" + i, "Software Engineer", "2026-10-01T00:00:00Z", ""));
        }
        FakeHttp http = new FakeHttp((m, u, b) -> Json.writeCompact(obj("totalFound", 900, "content", recent)));
        Instant alreadyPast = Instant.now().minus(Duration.ofSeconds(1));
        List<RawPosting> raw = BoardSource.of(Ats.SMARTRECRUITERS)
                .fetch(BOSCH, new BoardSource.Context(http, NOW, CUTOFF, alreadyPast));
        equal(100, raw.size(), "the first page is always read");
        equal(1, http.calls.size(), "no more pages once the time budget is spent");
    }

    // ---------------------------------------------------------------- Eightfold

    static final CompanyBoard EIGHTFOLD = new CompanyBoard("Acme", Ats.EIGHTFOLD, "careers.acme.com|acme.com");

    static Map<String, Object> position(int id, Instant posted) {
        return obj("id", id, "name", "Software Engineer " + id, "locations", List.of("India, Karnataka, Bangalore"),
                "postedTs", posted.getEpochSecond(), "positionUrl", "/careers/job/" + id);
    }

    @Test
    void eightfoldPagesUntilEverythingIsRead() throws Exception {
        FakeHttp http = new FakeHttp((m, url, b) -> {
            check(url.startsWith("https://careers.acme.com/api/pcsx/search?domain=acme.com&query=&location=India"), url);
            int start = Integer.parseInt(url.replaceAll(".*start=(\\d+).*", "$1"));
            List<Object> positions = new ArrayList<>();
            for (int i = start; i < Math.min(start + 10, 13); i++) {
                positions.add(position(i, NOW.minus(Duration.ofHours(i))));
            }
            return Json.writeCompact(obj("data", obj("count", 13, "positions", positions)));
        });
        List<RawPosting> raw = fetch(EIGHTFOLD, http);
        equal(13, raw.size());
        equal(2, http.calls.size());
        equal("https://careers.acme.com/careers/job/0", raw.get(0).url());
        equal(NOW.minus(Duration.ofHours(12)).getEpochSecond(), raw.get(12).postedAt().getEpochSecond());
    }

    @Test
    void eightfoldStopsAtTheCutoffAndFallsBackToV2() throws Exception {
        FakeHttp old = new FakeHttp((m, u, b) -> Json.writeCompact(obj("data", obj("count", 500, "positions",
                List.of(position(1, NOW), position(2, NOW.minus(Duration.ofDays(40))))))));
        fetch(EIGHTFOLD, old);
        equal(1, old.calls.size(), "newest-first results older than the cutoff end the scan");

        FakeHttp legacy = new FakeHttp((m, url, b) -> {
            if (url.contains("/api/pcsx/")) {
                throw new IOException("HTTP 403");
            }
            check(url.contains("/api/apply/v2/jobs?domain=acme.com"), url);
            return Json.writeCompact(obj("positions", List.of(obj("id", 7, "name", "Data Engineer",
                    "location", "Mumbai, India", "t_create", NOW.getEpochSecond(),
                    "canonicalPositionUrl", "https://careers.acme.com/job/7"))));
        });
        List<RawPosting> raw = fetch(EIGHTFOLD, legacy);
        equal(1, raw.size());
        equal("https://careers.acme.com/job/7", raw.get(0).url());
    }

    // ---------------------------------------------------------------- Oracle

    static final CompanyBoard ORACLE = new CompanyBoard("Bank", Ats.ORACLE, "hcm.example.com|CX_1");

    @Test
    void oracleLooksUpIndiaThenReadsNewestFirst() throws Exception {
        FakeHttp http = new FakeHttp((m, url, b) -> {
            check(url.startsWith("https://hcm.example.com/hcmRestApi/resources/latest/recruitingCEJobRequisitions"), url);
            if (!url.contains("locationId=")) {
                return Json.writeCompact(obj("items", List.of(obj("locationsFacet", List.of(
                        obj("Id", 1, "Name", "United States"), obj("Id", 999, "Name", "India"))))));
            }
            check(url.contains("locationId=999") && url.contains("sortBy=POSTING_DATES_DESC"), url);
            return Json.writeCompact(obj("items", List.of(obj("requisitionList", List.of(
                    obj("Id", "1", "Title", "Software Engineer II - Java", "PostedDate", "2026-09-30",
                            "PrimaryLocation", "Bengaluru, Karnataka, India"),
                    obj("Id", "2", "Title", "Data Analyst", "PostedDate", "2026-09-29",
                            "PrimaryLocation", "Mumbai, Maharashtra, India"))))));
        });
        List<RawPosting> raw = fetch(ORACLE, http);
        equal(2, raw.size());
        equal(2, http.calls.size());
        equal(Instant.parse("2026-09-30T00:00:00Z"), raw.get(0).postedAt());
        equal("https://hcm.example.com/hcmUI/CandidateExperience/en/sites/CX_1/job/1", raw.get(0).url());
    }

    // ---------------------------------------------------------------- Amazon

    @Test
    void amazonParsesItsDatesAndInternFlag() throws Exception {
        FakeHttp http = new FakeHttp((m, url, b) -> {
            check(url.contains("normalized_country_code%5B%5D=IND") && url.contains("sort=recent"), url);
            return Json.writeCompact(obj("hits", 2, "jobs", List.of(
                    obj("id_icims", "111", "title", "SDE II, Alexa", "posted_date", "September  5, 2026",
                            "normalized_location", "Bengaluru, Karnataka, IND", "job_path", "/en/jobs/111/sde-ii",
                            "is_intern", false, "job_schedule_type", "full-time"),
                    obj("id_icims", "112", "title", "Software Dev Engineer Intern", "posted_date", "October  1, 2026",
                            "normalized_location", "Hyderabad, Telangana, IND", "job_path", "/en/jobs/112/sde-intern",
                            "is_intern", true))));
        });
        List<RawPosting> raw = fetch(new CompanyBoard("Amazon", Ats.AMAZON, "amazon"), http);
        equal(Instant.parse("2026-09-05T00:00:00Z"), raw.get(0).postedAt());
        equal("https://www.amazon.jobs/en/jobs/111/sde-ii", raw.get(0).url());
        equal("Intern", raw.get(1).employmentHint());
    }

    // ---------------------------------------------------------------- Apple and Google (embedded page data)

    @Test
    void appleReadsTheJsonEmbeddedInItsSearchPage() throws Exception {
        String data = Json.writeCompact(obj("loaderData", obj("search", obj("totalRecords", 1, "searchResults",
                List.of(obj("positionId", "200313970", "postingTitle", "Software Engineer \"iOS\" – Maps",
                        "postDateInGMT", "2026-10-02T07:39:19.897495217Z",
                        "locations", List.of(obj("name", "Bengaluru")),
                        "transformedPostingTitle", "software-engineer-ios-maps"))))));
        String html = "<html><script>window.__staticRouterHydrationData = JSON.parse("
                + Json.writeCompact(data) + ");</script></html>";
        FakeHttp http = new FakeHttp((m, url, b) -> {
            check(url.startsWith("https://jobs.apple.com/en-in/search?location=india-INDC&sort=newest"), url);
            return html;
        });
        List<RawPosting> raw = fetch(new CompanyBoard("Apple", Ats.APPLE, "apple"), http);
        equal(1, raw.size());
        equal("Software Engineer \"iOS\" – Maps", raw.get(0).title());
        equal("https://jobs.apple.com/en-in/details/200313970/software-engineer-ios-maps", raw.get(0).url());
        equal(Instant.parse("2026-10-02T07:39:19.897495217Z"), raw.get(0).postedAt());

        FakeHttp changed = new FakeHttp((m, u, b) -> "<html>new layout</html>");
        check(fails(IOException.class, () -> fetch(new CompanyBoard("Apple", Ats.APPLE, "apple"), changed))
                .getMessage().contains("layout changed"), "clear error when Apple changes its page");
    }

    @Test
    void googleReadsThePositionalJobArrays() throws Exception {
        List<Object> job = new ArrayList<>(List.of("123", "Software Engineer III, Google Cloud", "https://signin"));
        while (job.size() < 9) {
            job.add(null);
        }
        job.add(List.of(List.of("Bengaluru, Karnataka, India", List.of(), "Bengaluru")));
        job.add(null);
        job.add(List.of(2));
        job.add(List.of(1790878129L, 775000000L));
        String html = "<script>AF_initDataCallback({key: 'ds:1', hash: '2', data:"
                + Json.writeCompact(java.util.Arrays.asList(List.of(job), null, 1, 20)) + ", sideChannel: {}});</script>";
        FakeHttp http = new FakeHttp((m, url, b) -> {
            check(url.contains("location=India") && url.contains("sort_by=date"), url);
            return html;
        });
        List<RawPosting> raw = fetch(new CompanyBoard("Google", Ats.GOOGLE, "google"), http);
        equal(1, raw.size());
        equal("https://www.google.com/about/careers/applications/jobs/results/123-software-engineer-iii-google-cloud",
                raw.get(0).url());
        equal(Instant.ofEpochSecond(1790878129L), raw.get(0).postedAt());
        equal("Bengaluru", JobScanner.extract(new CompanyBoard("Google", Ats.GOOGLE, "google"), raw, CUTOFF)
                .get(0).city());
    }
}
