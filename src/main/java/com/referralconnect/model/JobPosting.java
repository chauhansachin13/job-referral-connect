package com.referralconnect.model;

import com.referralconnect.json.Json;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A relevant, India-located opening found on a company job board.
 *
 * @param id         globally unique: board key + the ATS's own job id
 * @param companyKey {@link CompanyBoard#key()} of the board it came from
 * @param postedAt   when the posting was first published (falls back to last update)
 * @param city       canonical Indian city used for filtering, e.g. "Bengaluru"
 */
public record JobPosting(
        String id,
        String companyKey,
        String company,
        String title,
        String location,
        String city,
        JobCategory category,
        boolean internship,
        Instant postedAt,
        String url,
        Ats source) {

    public String typeLabel() {
        return internship ? "Internship" : "Full-time";
    }

    public long ageDays(Instant now) {
        return Math.max(0, Duration.between(postedAt, now).toDays());
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("companyKey", companyKey);
        m.put("company", company);
        m.put("title", title);
        m.put("location", location);
        m.put("city", city);
        m.put("category", category.name());
        m.put("internship", internship);
        m.put("postedAt", postedAt.toString());
        m.put("url", url);
        m.put("source", source.name());
        return m;
    }

    public static JobPosting fromJson(Map<String, Object> m) {
        return new JobPosting(
                Json.str(m, "id"),
                Json.str(m, "companyKey"),
                Json.str(m, "company"),
                Json.str(m, "title"),
                Json.str(m, "location"),
                Json.str(m, "city"),
                JobCategory.valueOf(Json.str(m, "category")),
                Json.bool(m, "internship"),
                Instant.parse(Json.str(m, "postedAt")),
                Json.str(m, "url"),
                Ats.valueOf(Json.str(m, "source")));
    }
}
