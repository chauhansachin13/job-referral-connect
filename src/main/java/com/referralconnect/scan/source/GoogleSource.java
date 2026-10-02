package com.referralconnect.scan.source;

import com.referralconnect.json.Json;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.scan.RawPosting;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Google Careers has no public JSON API; its results page embeds the jobs as a JSON array in an
 * {@code AF_initDataCallback({key: 'ds:1', … data: [...]})} block. Each job is a positional array:
 * [0] id, [1] title, [9] locations, [12] published time as [seconds, nanos].
 */
final class GoogleSource implements BoardSource {

    static final int MAX_PAGES = 15;

    @Override
    public List<RawPosting> fetch(CompanyBoard board, Context ctx) throws Exception {
        List<RawPosting> out = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            if (!out.isEmpty() && ctx.outOfTime()) {
                break; // this company has used its time: keep what we have
            }
            List<Object> data;
            try {
                data = Json.asArray(embeddedData(ctx.http().get(
                        "https://www.google.com/about/careers/applications/jobs/results/?location=India&sort_by=date&page="
                                + page)));
            } catch (IOException e) {
                if (out.isEmpty()) {
                    throw e;
                }
                break; // keep the newest pages we already have
            }
            List<Object> jobs = data.isEmpty() ? List.of() : Json.asArray(data.get(0));
            Instant oldest = null;
            for (Object o : jobs) {
                List<Object> j = Json.asArray(o);
                if (j.size() < 13) {
                    continue;
                }
                String id = String.valueOf(j.get(0));
                String title = String.valueOf(j.get(1)).trim();
                List<String> locations = new ArrayList<>();
                for (Object l : Json.asArray(j.get(9))) {
                    List<Object> loc = Json.asArray(l);
                    if (!loc.isEmpty()) {
                        locations.add(String.valueOf(loc.get(0)));
                    }
                }
                List<Object> ts = Json.asArray(j.get(12));
                Instant posted = ts.isEmpty() || !(ts.get(0) instanceof Number n) ? null : Instant.ofEpochSecond(n.longValue());
                if (posted != null && (oldest == null || posted.isBefore(oldest))) {
                    oldest = posted;
                }
                out.add(new RawPosting(id, title, locations, posted,
                        "https://www.google.com/about/careers/applications/jobs/results/" + id + "-" + slug(title), ""));
            }
            long total = data.size() > 2 && data.get(2) instanceof Number n ? n.longValue() : 0;
            if (jobs.isEmpty() || (long) page * jobs.size() >= total || (oldest != null && oldest.isBefore(ctx.cutoff()))) {
                break;
            }
        }
        return out;
    }

    static Object embeddedData(String html) throws IOException {
        int block = html.indexOf("AF_initDataCallback({key: 'ds:1'");
        int start = block < 0 ? -1 : html.indexOf("data:", block);
        int end = start < 0 ? -1 : html.indexOf(", sideChannel:", start);
        if (end < 0) {
            throw new IOException("Google careers page layout changed (no embedded job data)");
        }
        return Json.parse(html.substring(start + "data:".length(), end));
    }

    static String slug(String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
