package com.referralconnect.scan.source;

import com.referralconnect.json.Json;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.scan.AtsParsers;
import com.referralconnect.scan.RawPosting;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * iCIMS "Jibe" career sites (e.g. AMD's careers.amd.com) through the JSON endpoint their search page
 * uses: {@code /api/jobs?location=India}, newest first, 100 a page.
 */
final class JibeSource implements BoardSource {

    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 10;
    /** Jibe writes offsets without a colon: "2026-10-02T08:54:00+0000". */
    private static final DateTimeFormatter POSTED = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    @Override
    public List<RawPosting> fetch(CompanyBoard board, Context ctx) throws Exception {
        String host = board.token();
        List<RawPosting> out = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            if (!out.isEmpty() && ctx.outOfTime()) {
                break; // this company has used its time: keep what we have
            }
            String body;
            try {
                body = ctx.http().get("https://" + host + "/api/jobs?location=India&page=" + page
                        + "&sortBy=posted_date&descending=true&internal=false&limit=" + PAGE_SIZE);
            } catch (IOException e) {
                if (out.isEmpty()) {
                    throw e;
                }
                break; // keep the newest pages we already have
            }
            Map<String, Object> r = Json.asObject(Json.parse(body));
            List<Object> jobs = Json.arr(r, "jobs");
            Instant oldest = null;
            for (Object o : jobs) {
                Map<String, Object> d = Json.obj(Json.asObject(o), "data");
                Instant posted = date(Json.str(d, "posted_date"));
                if (posted != null && (oldest == null || posted.isBefore(oldest))) {
                    oldest = posted;
                }
                String slug = Json.str(d, "slug").isEmpty() ? Json.str(d, "req_id") : Json.str(d, "slug");
                out.add(new RawPosting(
                        slug,
                        Json.str(d, "title").trim(),
                        List.of(Json.str(d, "city") + ", " + Json.str(d, "state") + ", " + Json.str(d, "country"),
                                Json.str(d, "location_name"), Json.str(d, "country")),
                        posted,
                        "https://" + host + "/careers-home/jobs/" + slug,
                        Json.str(d, "employment_type"),
                        false,
                        Json.str(d, "description") + "\n" + Json.str(d, "qualifications")));
            }
            if (jobs.isEmpty() || (long) page * PAGE_SIZE >= Json.num(r, "totalCount", 0)
                    || (oldest != null && oldest.isBefore(ctx.cutoff()))) {
                break;
            }
        }
        return out;
    }

    static Instant date(String s) {
        try {
            return OffsetDateTime.parse(s, POSTED).toInstant();
        } catch (DateTimeParseException e) {
            return AtsParsers.parseTime(s);
        }
    }
}
