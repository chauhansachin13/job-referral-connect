package com.referralconnect.scan.source;

import com.referralconnect.json.Json;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.scan.RawPosting;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** amazon.jobs search JSON, restricted to India and sorted by most recent. */
final class AmazonSource implements BoardSource {

    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 15;
    private static final DateTimeFormatter POSTED = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    @Override
    public List<RawPosting> fetch(CompanyBoard board, Context ctx) throws Exception {
        List<RawPosting> out = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            if (!out.isEmpty() && ctx.outOfTime()) {
                break; // this company has used its time: keep what we have
            }
            int offset = page * PAGE_SIZE;
            String body;
            try {
                body = ctx.http().get("https://www.amazon.jobs/en/search.json?normalized_country_code%5B%5D=IND"
                        + "&result_limit=" + PAGE_SIZE + "&sort=recent&offset=" + offset);
            } catch (IOException e) {
                if (out.isEmpty()) {
                    throw e;
                }
                break; // keep the newest pages we already have
            }
            Map<String, Object> r = Json.asObject(Json.parse(body));
            List<Object> jobs = Json.arr(r, "jobs");
            boolean anyRecent = false;
            for (Object o : jobs) {
                Map<String, Object> j = Json.asObject(o);
                Instant posted = date(Json.str(j, "posted_date"));
                anyRecent |= posted != null && !posted.isBefore(ctx.cutoff());
                out.add(new RawPosting(
                        Json.str(j, "id_icims"),
                        Json.str(j, "title").trim(),
                        List.of(Json.str(j, "normalized_location"), Json.str(j, "location"), "India"),
                        posted,
                        "https://www.amazon.jobs" + Json.str(j, "job_path"),
                        Json.bool(j, "is_intern") ? "Intern" : Json.str(j, "job_schedule_type"),
                        false,
                        // Amazon lists the must-haves separately from the preferred qualifications.
                        Json.str(j, "basic_qualifications")));
            }
            if (jobs.isEmpty() || !anyRecent || offset + PAGE_SIZE >= Json.num(r, "hits", 0)) {
                break;
            }
        }
        return out;
    }

    /** "October  1, 2026" (Amazon pads single-digit days with a space). */
    static Instant date(String s) {
        try {
            return LocalDate.parse(s.trim().replaceAll("\\s+", " "), POSTED).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
