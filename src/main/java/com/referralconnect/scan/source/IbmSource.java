package com.referralconnect.scan.source;

import com.referralconnect.json.Json;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.scan.RawPosting;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IBM's careers search (the search service behind careers.ibm.com), filtered to India and sorted by
 * date. Field names are IBM's: {@code field_keyword_05} is the country, {@code _19} the location and
 * {@code _18} the experience level.
 */
final class IbmSource implements BoardSource {

    static final String API = "https://www-api.ibm.com/search/api/v2";
    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 10;

    @Override
    public List<RawPosting> fetch(CompanyBoard board, Context ctx) throws Exception {
        List<RawPosting> out = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            if (!out.isEmpty() && ctx.outOfTime()) {
                break; // this company has used its time: keep what we have
            }
            String body;
            try {
                body = ctx.http().post(API, query(page * PAGE_SIZE));
            } catch (IOException e) {
                if (out.isEmpty()) {
                    throw e;
                }
                break; // keep the newest pages we already have
            }
            Map<String, Object> hits = Json.obj(Json.asObject(Json.parse(body)), "hits");
            List<Object> list = Json.arr(hits, "hits");
            Instant oldest = null;
            for (Object o : list) {
                Map<String, Object> h = Json.asObject(o);
                Map<String, Object> s = Json.obj(h, "_source");
                Instant posted = date(Json.str(s, "dcdate"));
                if (posted != null && (oldest == null || posted.isBefore(oldest))) {
                    oldest = posted;
                }
                out.add(new RawPosting(Json.str(h, "_id"), Json.str(s, "title").trim(),
                        List.of(Json.str(s, "field_keyword_19"), "India"), posted, Json.str(s, "url"),
                        Json.str(s, "field_keyword_18")));
            }
            long total = Json.num(Json.obj(hits, "total"), "value", 0);
            if (list.isEmpty() || (long) (page + 1) * PAGE_SIZE >= total
                    || (oldest != null && oldest.isBefore(ctx.cutoff()))) {
                break;
            }
        }
        return out;
    }

    static String query(int from) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("appId", "careers");
        q.put("scopes", List.of("careers2"));
        q.put("query", Map.of("bool", Map.of("must", List.of())));
        q.put("post_filter", Map.of("term", Map.of("field_keyword_05", "India")));
        q.put("size", PAGE_SIZE);
        q.put("from", from);
        q.put("sort", List.of(Map.of("dcdate", "desc")));
        q.put("lang", "zz");
        q.put("localLanguage", "en");
        q.put("sm", Map.of("query", "", "lang", "zz"));
        q.put("_source", List.of("title", "url", "dcdate", "field_keyword_05", "field_keyword_18", "field_keyword_19"));
        return Json.writeCompact(q);
    }

    private static Instant date(String s) {
        try {
            return s.isBlank() ? null : LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
