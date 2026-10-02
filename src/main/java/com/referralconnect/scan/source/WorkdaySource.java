package com.referralconnect.scan.source;

import com.referralconnect.json.Json;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.scan.IndiaLocations;
import com.referralconnect.scan.RawPosting;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workday career sites (NVIDIA, Salesforce, Adobe, Intel, Cisco, Citi, …) through the JSON
 * endpoint their own search page calls: {@code POST /wday/cxs/{tenant}/{site}/jobs}.
 *
 * <p>Every company names its location filter differently ("locationCountry", "Location_Country",
 * "locationHierarchy1", …), so the filter is discovered from the facets the site returns: a
 * country value called "India" if there is one, otherwise every location value in India.
 * Workday does not sort by date, so all India results are read (up to {@link #MAX_POSTINGS}).
 */
final class WorkdaySource implements BoardSource {

    static final int PAGE_SIZE = 20;
    static final int MAX_POSTINGS = 1000;
    private static final int PARALLEL_PAGES = 4;
    private static final Pattern DAYS_AGO = Pattern.compile("(\\d+)(\\+?)\\s*days?\\s*ago", Pattern.CASE_INSENSITIVE);

    @Override
    public List<RawPosting> fetch(CompanyBoard board, Context ctx) throws Exception {
        String[] p = board.tokenParts();
        if (p.length != 3) {
            throw new IOException("Workday board must look like tenant/wdN/site, got " + board.token());
        }
        String host = "https://" + p[0] + "." + p[1] + ".myworkdayjobs.com";
        String api = host + "/wday/cxs/" + p[0] + "/" + p[2] + "/jobs";
        String jobBase = host + "/en-US/" + p[2];

        Map<String, List<String>> india = indiaFilter(Json.parse(ctx.http().post(api, body(Map.of(), 0, 1))));
        if (india.isEmpty()) {
            throw new IOException("this Workday site has no India location filter");
        }
        Map<String, Object> first = Json.asObject(Json.parse(ctx.http().post(api, body(india, 0, PAGE_SIZE))));
        List<RawPosting> out = new ArrayList<>(convert(first, jobBase, ctx.now()));
        // Workday only reports the total on the first page.
        int total = (int) Math.min(Json.num(first, "total", 0), MAX_POSTINGS);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Semaphore permits = new Semaphore(PARALLEL_PAGES);
            List<Future<List<RawPosting>>> pages = new ArrayList<>();
            for (int offset = PAGE_SIZE; offset < total; offset += PAGE_SIZE) {
                int o = offset;
                pages.add(pool.submit(() -> {
                    permits.acquire();
                    try {
                        if (ctx.outOfTime()) {
                            return List.<RawPosting>of(); // this company has used its time
                        }
                        return convert(Json.asObject(Json.parse(ctx.http().post(api, body(india, o, PAGE_SIZE)))),
                                jobBase, ctx.now());
                    } finally {
                        permits.release();
                    }
                }));
            }
            for (Future<List<RawPosting>> page : pages) {
                try {
                    out.addAll(page.get());
                } catch (ExecutionException e) {
                    // A page that still fails after retries is skipped; the rest of the board is kept.
                    if (!(e.getCause() instanceof IOException)) {
                        throw e.getCause() instanceof Exception ex ? ex : e;
                    }
                }
            }
        }
        return out;
    }

    private static String body(Map<String, List<String>> facets, int offset, int limit) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("appliedFacets", facets);
        b.put("limit", limit);
        b.put("offset", offset);
        b.put("searchText", "");
        return Json.writeCompact(b);
    }

    /**
     * facetParameter → value ids that select India. Prefers a single country value named
     * exactly "India"; falls back to all Indian city values of the richest location facet.
     */
    static Map<String, List<String>> indiaFilter(Object response) {
        List<String[]> values = new ArrayList<>(); // {facetParameter, descriptor, id}
        collect(response, null, values);
        String[] country = null;
        for (String[] v : values) {
            if (v[1].trim().equalsIgnoreCase("India")
                    && (country == null || v[0].toLowerCase(Locale.ROOT).contains("country"))) {
                country = v;
            }
        }
        Map<String, List<String>> filter = new LinkedHashMap<>();
        if (country != null) {
            filter.put(country[0], List.of(country[2]));
            return filter;
        }
        Map<String, List<String>> byParam = new LinkedHashMap<>();
        for (String[] v : values) {
            if (IndiaLocations.isIndia(v[1])) {
                byParam.computeIfAbsent(v[0], k -> new ArrayList<>()).add(v[2]);
            }
        }
        byParam.entrySet().stream()
                .max((a, b) -> Integer.compare(a.getValue().size(), b.getValue().size()))
                .ifPresent(e -> filter.put(e.getKey(), e.getValue()));
        return filter;
    }

    private static void collect(Object node, String param, List<String[]> out) {
        if (node instanceof Map<?, ?> m) {
            Object fp = m.get("facetParameter");
            String here = fp == null ? param : fp.toString();
            Object descriptor = m.get("descriptor");
            Object id = m.get("id");
            if (param != null && descriptor != null && id != null) {
                out.add(new String[]{param, descriptor.toString(), id.toString()});
            }
            for (Object child : m.values()) {
                collect(child, here, out);
            }
        } else if (node instanceof List<?> list) {
            for (Object child : list) {
                collect(child, param, out);
            }
        }
    }

    private static List<RawPosting> convert(Map<String, Object> page, String jobBase, Instant now) {
        List<RawPosting> out = new ArrayList<>();
        for (Object o : Json.arr(page, "jobPostings")) {
            Map<String, Object> j = Json.asObject(o);
            String path = Json.str(j, "externalPath");
            if (path.isEmpty()) {
                continue;
            }
            List<String> locations = new ArrayList<>();
            String text = Json.str(j, "locationsText");
            locations.add(text);
            // "2 Locations" says nothing useful; the job's URL path usually names the main city.
            if (!IndiaLocations.isIndia(text)) {
                locations.add(locationFromPath(path));
            }
            for (Object bullet : Json.arr(j, "bulletFields")) {
                locations.add(String.valueOf(bullet));
            }
            locations.add("India"); // the request was already filtered to India
            out.add(new RawPosting(path, Json.str(j, "title").trim(), locations,
                    postedAt(Json.str(j, "postedOn"), now), jobBase + path, Json.str(j, "timeType")));
        }
        return out;
    }

    /** "/job/India-Bengaluru/Software-Engineer_JR123" → "India Bengaluru". */
    static String locationFromPath(String path) {
        String[] parts = path.split("/");
        return parts.length > 2 ? parts[2].replace('-', ' ') : "";
    }

    /**
     * "Posted Today", "Posted Yesterday", "Posted 3 Days Ago", "Posted 30+ Days Ago" → the start of
     * that day (UTC). Workday only says which day, so a "today" posting must not look minutes old.
     */
    static Instant postedAt(String postedOn, Instant now) {
        String s = postedOn == null ? "" : postedOn.toLowerCase(Locale.ROOT);
        Instant today = now.truncatedTo(ChronoUnit.DAYS);
        if (s.contains("today")) {
            return today;
        }
        if (s.contains("yesterday")) {
            return today.minus(Duration.ofDays(1));
        }
        Matcher m = DAYS_AGO.matcher(s);
        if (m.find()) {
            long days = Long.parseLong(m.group(1)) + (m.group(2).isEmpty() ? 0 : 1);
            return today.minus(Duration.ofDays(days));
        }
        return null;
    }
}
