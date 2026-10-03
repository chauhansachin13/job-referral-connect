package com.referralconnect.scan.source;

import com.referralconnect.json.Json;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.scan.RawPosting;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Eightfold-hosted career sites (Microsoft, Qualcomm, Morgan Stanley, …). Newer sites answer the
 * "PCSX" search endpoint (10 results a page, newest first); older ones only the v2 jobs endpoint.
 *
 * <p>All these sites share Eightfold's infrastructure and rate limits, so requests to them are
 * throttled together: at most two at a time, with a short pause after each.
 */
final class EightfoldSource implements BoardSource {

    /** 10 results a page: the newest 250 openings per company, which covers 30 days for most. */
    static final int MAX_PAGES = 25;
    private static final Semaphore SHARED = new Semaphore(2);
    private static final long PAUSE_MS = 250;

    @Override
    public List<RawPosting> fetch(CompanyBoard board, Context ctx) throws Exception {
        String[] p = board.tokenParts();
        if (p.length != 2) {
            throw new IOException("Eightfold board must look like host|domain, got " + board.token());
        }
        try {
            return pcsx(p[0], p[1], ctx);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("403")) {
                return v2(p[0], p[1], ctx);
            }
            throw e;
        }
    }

    /** The search results have no description; position_details does (newer PCSX sites only). */
    @Override
    public String details(CompanyBoard board, RawPosting posting, Context ctx) throws Exception {
        String[] p = board.tokenParts();
        String body = get(ctx, "https://" + p[0] + "/api/pcsx/position_details?position_id=" + posting.atsId()
                + "&domain=" + p[1] + "&hl=en");
        return Json.str(Json.obj(Json.asObject(Json.parse(body)), "data"), "jobDescription");
    }

    /** Few per scan: these sites throttle hard, and the listing must stay fast. Later scans add more. */
    @Override
    public int detailsPerScan(CompanyBoard board) {
        return 40;
    }

    private static String get(Context ctx, String url) throws Exception {
        SHARED.acquire();
        try {
            return ctx.http().get(url);
        } finally {
            Thread.sleep(PAUSE_MS);
            SHARED.release();
        }
    }

    private static List<RawPosting> pcsx(String host, String domain, Context ctx) throws Exception {
        List<RawPosting> out = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            if (!out.isEmpty() && ctx.outOfTime()) {
                break; // this company has used its time: keep what we have
            }
            Map<String, Object> data;
            try {
                data = Json.obj(Json.asObject(Json.parse(get(ctx, "https://" + host + "/api/pcsx/search?domain="
                        + domain + "&query=&location=India&start=" + out.size() + "&sort_by=timestamp"))), "data");
            } catch (IOException e) {
                if (out.isEmpty()) {
                    throw e;
                }
                break; // keep the newest pages we already have
            }
            List<Object> positions = Json.arr(data, "positions");
            Instant oldest = null;
            for (Object o : positions) {
                Map<String, Object> j = Json.asObject(o);
                long ts = Json.num(j, "postedTs", -1);
                Instant posted = ts > 0 ? Instant.ofEpochSecond(ts) : null;
                if (posted != null && (oldest == null || posted.isBefore(oldest))) {
                    oldest = posted;
                }
                List<String> locations = new ArrayList<>();
                Json.arr(j, "locations").forEach(l -> locations.add(String.valueOf(l)));
                if (locations.isEmpty()) {
                    // The same places again in short form ("Bengaluru, KA, IN"); only needed as a fallback.
                    Json.arr(j, "standardizedLocations").forEach(l -> locations.add(String.valueOf(l)));
                }
                String path = Json.str(j, "positionUrl");
                out.add(new RawPosting(Json.str(j, "id"), Json.str(j, "name").trim(), locations, posted,
                        path.startsWith("/") ? "https://" + host + path : path, ""));
            }
            if (positions.isEmpty() || out.size() >= Json.num(data, "count", 0)
                    || (oldest != null && oldest.isBefore(ctx.cutoff()))) {
                break;
            }
        }
        return out;
    }

    private static List<RawPosting> v2(String host, String domain, Context ctx) throws Exception {
        List<RawPosting> out = new ArrayList<>();
        for (int page = 0; page < 5; page++) {
            if (!out.isEmpty() && ctx.outOfTime()) {
                break; // this company has used its time: keep what we have
            }
            Map<String, Object> r = Json.asObject(Json.parse(get(ctx, "https://" + host
                    + "/api/apply/v2/jobs?domain=" + domain + "&start=" + out.size()
                    + "&num=100&location=India&sort_by=timestamp")));
            List<Object> positions = Json.arr(r, "positions");
            for (Object o : positions) {
                Map<String, Object> j = Json.asObject(o);
                long ts = Json.num(j, "t_create", -1);
                List<String> locations = new ArrayList<>();
                locations.add(Json.str(j, "location"));
                Json.arr(j, "locations").forEach(l -> locations.add(String.valueOf(l)));
                out.add(new RawPosting(Json.str(j, "id"), Json.str(j, "name").trim(), locations,
                        ts > 0 ? Instant.ofEpochSecond(ts) : null, Json.str(j, "canonicalPositionUrl"), ""));
            }
            if (positions.size() < 100) {
                break;
            }
        }
        return out;
    }
}
