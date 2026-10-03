package com.referralconnect.model;

import com.referralconnect.json.Json;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A relevant, India-located opening found on a company careers site.
 *
 * @param id           globally unique: board key + the site's own job id
 * @param companyKey   {@link CompanyBoard#key()} of the board it came from
 * @param postedAt     when the posting was published; when {@code dateKnown} is false, when this app
 *                     first saw it (the company does not publish posting dates)
 * @param city         canonical Indian city used for filtering, e.g. "Bengaluru"
 * @param requirements minimum experience, degree, batch and skills, as far as the posting says
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
        Ats source,
        boolean dateKnown,
        Requirements requirements) {

    public JobPosting {
        requirements = requirements == null ? Requirements.UNKNOWN : requirements;
    }

    public JobPosting(String id, String companyKey, String company, String title, String location, String city,
                      JobCategory category, boolean internship, Instant postedAt, String url, Ats source,
                      boolean dateKnown) {
        this(id, companyKey, company, title, location, city, category, internship, postedAt, url, source, dateKnown,
                Requirements.UNKNOWN);
    }

    public JobPosting(String id, String companyKey, String company, String title, String location, String city,
                      JobCategory category, boolean internship, Instant postedAt, String url, Ats source) {
        this(id, companyKey, company, title, location, city, category, internship, postedAt, url, source, true);
    }

    public String typeLabel() {
        return internship ? "Internship" : "Full-time";
    }

    /** The minimum experience the posting states: "3+ yrs", "2–5 yrs", "Fresher", "Pref. 5+ yrs", "Not stated". */
    public String experienceLabel() {
        return requirements.shortLabel();
    }

    public long ageDays(Instant now) {
        return Math.max(0, Duration.between(postedAt, now).toDays());
    }

    public JobPosting withPostedAt(Instant at) {
        return new JobPosting(id, companyKey, company, title, location, city, category, internship, at, url, source,
                dateKnown, requirements);
    }

    public JobPosting withRequirements(Requirements r) {
        return new JobPosting(id, companyKey, company, title, location, city, category, internship, postedAt, url,
                source, dateKnown, r);
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
        if (!dateKnown) {
            m.put("dateKnown", false);
        }
        if (!requirements.equals(Requirements.UNKNOWN)) {
            m.put("requirements", requirements.toJson());
        }
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
                Ats.valueOf(Json.str(m, "source")),
                !m.containsKey("dateKnown") || Json.bool(m, "dateKnown"),
                Requirements.fromJson(Json.obj(m, "requirements")));
    }
}
