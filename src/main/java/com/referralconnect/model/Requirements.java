package com.referralconnect.model;

import com.referralconnect.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who can apply: the minimum experience a posting asks for, the degree and graduating batch it
 * names, and the skills it mentions.
 *
 * @param minYears    minimum years of experience; 0 for freshers; -1 when unknown
 * @param maxYears    upper end of a stated range ("2-5 years"), else -1
 * @param basis       whether the years were stated in the posting or estimated from the job level
 * @param evidence    the sentence the years came from, or what the estimate is based on
 * @param degree      lowest degree that qualifies, e.g. "Bachelor's in CS or a related field"
 * @param batch       graduating batches the posting is open to, e.g. "2025, 2026"
 * @param skills      technologies the posting mentions, most prominent first
 * @param detailsRead true once the full job description has been read; the scanner does not fetch
 *                    the description of such a job again
 */
public record Requirements(
        int minYears,
        int maxYears,
        Basis basis,
        String evidence,
        String degree,
        String batch,
        List<String> skills,
        boolean detailsRead) {

    public enum Basis {
        STATED("stated in the posting"),
        ESTIMATED("estimated from the job level"),
        UNKNOWN("not stated");

        private final String label;

        Basis(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final Requirements UNKNOWN = new Requirements(-1, -1, Basis.UNKNOWN, "", "", "", List.of(), false);

    public Requirements {
        basis = minYears < 0 ? Basis.UNKNOWN : basis;
        maxYears = maxYears < minYears ? -1 : maxYears;
        evidence = evidence == null ? "" : evidence;
        degree = degree == null ? "" : degree;
        batch = batch == null ? "" : batch;
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    public boolean known() {
        return minYears >= 0;
    }

    public boolean estimated() {
        return basis == Basis.ESTIMATED;
    }

    /** True when someone with {@code years} of experience meets the minimum (or it is unknown). */
    public boolean fits(int years) {
        return !known() || years >= minYears;
    }

    /** Compact form for table cells: "Fresher", "0–2 yrs", "3+ yrs", "~5+ yrs", "—". */
    public String shortLabel() {
        if (!known()) {
            return "—";
        }
        String prefix = estimated() ? "~" : "";
        if (maxYears > minYears) {
            return prefix + minYears + "–" + maxYears + " yrs";
        }
        return minYears == 0 ? prefix + "Fresher" : prefix + minYears + "+ yrs";
    }

    /** "3–5 years (stated in the posting)", "Freshers welcome (stated in the posting)". */
    public String longLabel() {
        if (!known()) {
            return "Not stated in the listing";
        }
        String years;
        if (maxYears > minYears) {
            years = minYears + "–" + maxYears + " years";
        } else if (minYears == 0) {
            years = "Freshers welcome (0 years)";
        } else {
            years = minYears + "+ " + (minYears == 1 ? "year" : "years");
        }
        return years + " (" + basis.label() + ")";
    }

    public Requirements withDetailsRead() {
        return new Requirements(minYears, maxYears, basis, evidence, degree, batch, skills, true);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("min", minYears);
        if (maxYears >= 0) {
            m.put("max", maxYears);
        }
        m.put("basis", basis.name());
        if (!evidence.isEmpty()) {
            m.put("evidence", evidence);
        }
        if (!degree.isEmpty()) {
            m.put("degree", degree);
        }
        if (!batch.isEmpty()) {
            m.put("batch", batch);
        }
        if (!skills.isEmpty()) {
            m.put("skills", skills);
        }
        if (detailsRead) {
            m.put("detailsRead", true);
        }
        return m;
    }

    public static Requirements fromJson(Map<String, Object> m) {
        if (m.isEmpty()) {
            return UNKNOWN;
        }
        List<String> skills = new ArrayList<>();
        for (Object o : Json.arr(m, "skills")) {
            skills.add(String.valueOf(o));
        }
        String basis = Json.str(m, "basis");
        return new Requirements(
                (int) Json.num(m, "min", -1),
                (int) Json.num(m, "max", -1),
                basis.isEmpty() ? Basis.UNKNOWN : Basis.valueOf(basis),
                Json.str(m, "evidence"),
                Json.str(m, "degree"),
                Json.str(m, "batch"),
                skills,
                m.containsKey("detailsRead") && Json.bool(m, "detailsRead"));
    }
}
