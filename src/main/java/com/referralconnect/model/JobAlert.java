package com.referralconnect.model;

import com.referralconnect.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A saved search. Every scan, openings that match it and appeared after {@code checkedAt} count
 * as new until the seeker opens the alert.
 *
 * @param query      words that must appear in the title, company or location (may be empty)
 * @param category   role family, or null for any
 * @param city       canonical city, or empty for anywhere in India
 * @param internship null for both, true for internships only, false for full-time only
 * @param maxYears   the most experience a matching job may ask for, or -1 for any
 */
public record JobAlert(
        String id,
        String accountId,
        String name,
        String query,
        JobCategory category,
        String city,
        Boolean internship,
        int maxYears,
        Instant createdAt,
        Instant checkedAt) {

    public JobAlert {
        name = name == null ? "" : name.trim();
        query = query == null ? "" : query.trim();
        city = city == null ? "" : city.trim();
    }

    public boolean matches(JobPosting j) {
        if (category != null && j.category() != category) {
            return false;
        }
        if (!city.isEmpty() && !j.city().contains(city)) {
            return false;
        }
        if (internship != null && j.internship() != internship) {
            return false;
        }
        if (maxYears >= 0 && j.requirements().known() && j.requirements().minYears() > maxYears) {
            return false;
        }
        if (!query.isEmpty()) {
            String hay = (j.title() + " " + j.company() + " " + j.location()).toLowerCase(Locale.ROOT);
            for (String word : query.toLowerCase(Locale.ROOT).split("\\s+")) {
                if (!hay.contains(word)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** "Data Scientist · Bengaluru · ≤ 2 yrs · "python"" — what the alert looks for. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(category == null ? "Any role" : category.label());
        sb.append(" · ").append(city.isEmpty() ? "All of India" : city);
        if (internship != null) {
            sb.append(" · ").append(internship ? "Internships" : "Full-time");
        }
        if (maxYears >= 0) {
            sb.append(" · ").append(maxYears == 0 ? "Freshers" : "≤ " + maxYears + " yrs");
        }
        if (!query.isEmpty()) {
            sb.append(" · \"").append(query).append('"');
        }
        return sb.toString();
    }

    public JobAlert checked(Instant at) {
        return new JobAlert(id, accountId, name, query, category, city, internship, maxYears, createdAt, at);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("accountId", accountId);
        m.put("name", name);
        m.put("query", query);
        m.put("category", category == null ? null : category.name());
        m.put("city", city);
        m.put("internship", internship);
        m.put("maxYears", maxYears);
        m.put("createdAt", createdAt.toString());
        m.put("checkedAt", checkedAt.toString());
        return m;
    }

    public static JobAlert fromJson(Map<String, Object> m) {
        String category = Json.str(m, "category");
        Object internship = m.get("internship");
        return new JobAlert(
                Json.str(m, "id"),
                Json.str(m, "accountId"),
                Json.str(m, "name"),
                Json.str(m, "query"),
                category.isEmpty() ? null : JobCategory.valueOf(category),
                Json.str(m, "city"),
                internship instanceof Boolean b ? b : null,
                (int) Json.num(m, "maxYears", -1),
                Instant.parse(Json.str(m, "createdAt")),
                Instant.parse(Json.str(m, "checkedAt")));
    }
}
