package com.referralconnect.scan;

import com.referralconnect.json.Json;
import com.referralconnect.model.Ats;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns each ATS's public job-board JSON into {@link RawPosting}s. */
public final class AtsParsers {

    private AtsParsers() {
    }

    public static List<RawPosting> parse(Ats ats, String body) {
        Object root = Json.parse(body);
        return switch (ats) {
            case GREENHOUSE -> greenhouse(root);
            case LEVER -> lever(root);
            case ASHBY -> ashby(root);
        };
    }

    /** {@code {"jobs":[{"id","title","location":{"name"},"first_published","updated_at","absolute_url"}]}} */
    static List<RawPosting> greenhouse(Object root) {
        List<RawPosting> out = new ArrayList<>();
        for (Object o : Json.arr(Json.asObject(root), "jobs")) {
            Map<String, Object> j = Json.asObject(o);
            String published = Json.str(j, "first_published");
            Instant postedAt = parseTime(published.isEmpty() ? Json.str(j, "updated_at") : published);
            out.add(new RawPosting(
                    Json.str(j, "id"),
                    Json.str(j, "title").trim(),
                    List.of(Json.str(Json.obj(j, "location"), "name")),
                    postedAt,
                    Json.str(j, "absolute_url"),
                    ""));
        }
        return out;
    }

    /** {@code [{"id","text","categories":{"location","allLocations","commitment"},"country","createdAt","hostedUrl"}]} */
    static List<RawPosting> lever(Object root) {
        List<RawPosting> out = new ArrayList<>();
        for (Object o : Json.asArray(root)) {
            Map<String, Object> j = Json.asObject(o);
            Map<String, Object> cat = Json.obj(j, "categories");
            List<String> locations = new ArrayList<>();
            locations.add(Json.str(cat, "location"));
            for (Object loc : Json.arr(cat, "allLocations")) {
                locations.add(String.valueOf(loc));
            }
            // Lever reports the ISO country code separately; "IN" means India even if the
            // location text is just a city we do not list.
            if ("IN".equalsIgnoreCase(Json.str(j, "country"))) {
                locations.add("India");
            }
            long createdAt = Json.num(j, "createdAt", -1);
            out.add(new RawPosting(
                    Json.str(j, "id"),
                    Json.str(j, "text").trim(),
                    locations,
                    createdAt > 0 ? Instant.ofEpochMilli(createdAt) : null,
                    Json.str(j, "hostedUrl"),
                    Json.str(cat, "commitment")));
        }
        return out;
    }

    /** {@code {"jobs":[{"id","title","location","secondaryLocations":[{"location"}],"address","employmentType","publishedAt","jobUrl","isListed"}]}} */
    static List<RawPosting> ashby(Object root) {
        List<RawPosting> out = new ArrayList<>();
        for (Object o : Json.arr(Json.asObject(root), "jobs")) {
            Map<String, Object> j = Json.asObject(o);
            if (j.containsKey("isListed") && !Json.bool(j, "isListed")) {
                continue;
            }
            List<String> locations = new ArrayList<>();
            locations.add(Json.str(j, "location"));
            locations.add(Json.str(Json.obj(Json.obj(j, "address"), "postalAddress"), "addressCountry"));
            for (Object s : Json.arr(j, "secondaryLocations")) {
                Map<String, Object> sec = Json.asObject(s);
                locations.add(Json.str(sec, "location"));
                locations.add(Json.str(Json.obj(Json.obj(sec, "address"), "postalAddress"), "addressCountry"));
            }
            out.add(new RawPosting(
                    Json.str(j, "id"),
                    Json.str(j, "title").trim(),
                    locations,
                    parseTime(Json.str(j, "publishedAt")),
                    Json.str(j, "jobUrl"),
                    Json.str(j, "employmentType")));
        }
        return out;
    }

    static Instant parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }
}
