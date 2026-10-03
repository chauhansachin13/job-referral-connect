package com.referralconnect.scan.source;

import com.referralconnect.json.Json;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.scan.AtsParsers;
import com.referralconnect.scan.RawPosting;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * jobs.apple.com has no public JSON API, but each search page embeds its results as JSON
 * ({@code window.__staticRouterHydrationData = JSON.parse("…")}). This reads the India search,
 * newest first, 20 results a page.
 */
final class AppleSource implements BoardSource {

    static final int MAX_PAGES = 12;
    private static final String MARKER = "window.__staticRouterHydrationData = JSON.parse(";

    @Override
    public List<RawPosting> fetch(CompanyBoard board, Context ctx) throws Exception {
        List<RawPosting> out = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            if (!out.isEmpty() && ctx.outOfTime()) {
                break; // this company has used its time: keep what we have
            }
            Object data;
            try {
                data = hydrationData(ctx.http().get(
                        "https://jobs.apple.com/en-in/search?location=india-INDC&sort=newest&page=" + page));
            } catch (IOException e) {
                if (out.isEmpty()) {
                    throw e;
                }
                break; // keep the newest pages we already have
            }
            List<Object> results = findList(data, "positionId");
            Instant oldest = null;
            for (Object o : results) {
                Map<String, Object> j = Json.asObject(o);
                Instant posted = AtsParsers.parseTime(Json.str(j, "postDateInGMT"));
                if (posted != null && (oldest == null || posted.isBefore(oldest))) {
                    oldest = posted;
                }
                List<String> locations = new ArrayList<>();
                for (Object l : Json.arr(j, "locations")) {
                    locations.add(Json.str(Json.asObject(l), "name"));
                }
                locations.add("India");
                String id = Json.str(j, "positionId");
                out.add(new RawPosting(id, Json.str(j, "postingTitle").trim(), locations, posted,
                        "https://jobs.apple.com/en-in/details/" + id + "/" + Json.str(j, "transformedPostingTitle"),
                        ""));
            }
            long total = findNumber(data, "totalRecords");
            if (results.isEmpty() || (long) page * 20 >= total || (oldest != null && oldest.isBefore(ctx.cutoff()))) {
                break;
            }
        }
        return out;
    }

    /** Each job's page embeds the same kind of JSON, with a "minimumQualifications" field. */
    @Override
    public String details(CompanyBoard board, RawPosting posting, Context ctx) throws Exception {
        return findString(hydrationData(ctx.http().get(posting.url())), "minimumQualifications");
    }

    @Override
    public int detailsPerScan(CompanyBoard board) {
        return 120;
    }

    /** The first string value stored under the given key anywhere in the tree, or "". */
    static String findString(Object node, String key) {
        if (node instanceof Map<?, ?> map) {
            if (map.get(key) instanceof String s) {
                return s;
            }
            for (Object child : map.values()) {
                String found = findString(child, key);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object child : list) {
                String found = findString(child, key);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }

    /** Extracts and decodes the JSON string literal passed to JSON.parse in the page. */
    static Object hydrationData(String html) throws IOException {
        int at = html.indexOf(MARKER);
        if (at < 0) {
            throw new IOException("Apple careers page layout changed (no embedded job data)");
        }
        int start = html.indexOf('"', at + MARKER.length());
        int i = start + 1;
        while (i < html.length()) {
            char c = html.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '"') {
                break;
            }
            i++;
        }
        // The literal uses JSON string escaping, so the JSON parser can decode it directly.
        String json = (String) Json.parse(html.substring(start, i + 1));
        return Json.parse(json);
    }

    /** The first list anywhere in the tree whose objects carry the given key. */
    static List<Object> findList(Object node, String key) {
        if (node instanceof List<?> list) {
            if (!list.isEmpty() && list.get(0) instanceof Map<?, ?> m && m.containsKey(key)) {
                return Json.asArray(list);
            }
            for (Object child : list) {
                List<Object> found = findList(child, key);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        } else if (node instanceof Map<?, ?> map) {
            for (Object child : map.values()) {
                List<Object> found = findList(child, key);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return List.of();
    }

    static long findNumber(Object node, String key) {
        if (node instanceof Map<?, ?> map) {
            if (map.get(key) instanceof Number n) {
                return n.longValue();
            }
            for (Object child : map.values()) {
                long found = findNumber(child, key);
                if (found >= 0) {
                    return found;
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object child : list) {
                long found = findNumber(child, key);
                if (found >= 0) {
                    return found;
                }
            }
        }
        return -1;
    }
}
