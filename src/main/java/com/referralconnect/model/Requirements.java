package com.referralconnect.model;

import com.referralconnect.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who can apply, exactly as the posting states it: the minimum years of experience, the degree and
 * graduating batch it names, and the skills it mentions. Nothing here is guessed.
 *
 * @param minYears    minimum years of experience exactly as the posting states it ("7.5" stays 7.5); 0 for
 *                    "freshers"; -1 when not stated
 * @param maxYears    upper end of a stated range ("2-5 years"), else -1
 * @param basis       whether the years are the posting's requirement, only its preference, or not stated
 * @param evidence    the sentence the years came from
 * @param degree      lowest degree that qualifies, e.g. "Bachelor's in CS or a related field"
 * @param batch       graduating batches the posting is open to, e.g. "2025, 2026"
 * @param skills      technologies the posting mentions, most prominent first
 * @param detailsRead true once the full job description has been read; the scanner does not fetch
 *                    the description of such a job again
 * @param version     which version of the reader produced this; older results are read again
 */
public record Requirements(
        double minYears,
        double maxYears,
        Basis basis,
        String evidence,
        String degree,
        String batch,
        List<String> skills,
        boolean detailsRead,
        int version) {

    /** Bumped whenever the way requirements are read changes, so stored results are refreshed. */
    public static final int CURRENT_VERSION = 4;

    public enum Basis {
        STATED("stated in the posting"),
        PREFERRED("preferred in the posting; it states no minimum"),
        UNKNOWN("not stated in the posting");

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

    public Requirements(double minYears, double maxYears, Basis basis, String evidence, String degree, String batch,
                        List<String> skills, boolean detailsRead) {
        this(minYears, maxYears, basis, evidence, degree, batch, skills, detailsRead, CURRENT_VERSION);
    }

    /** True when the posting gives a number of years (as a requirement or a preference). */
    public boolean known() {
        return minYears >= 0;
    }

    public boolean preferredOnly() {
        return basis == Basis.PREFERRED;
    }

    /** True when someone with {@code years} of experience meets the stated minimum (or none is stated). */
    public boolean fits(int years) {
        return !known() || years >= minYears;
    }

    /** "3", "7.5", "0.6": a number of years as the posting wrote it. */
    public static String years(double years) {
        double tenths = Math.round(years * 10) / 10.0;
        return tenths == Math.rint(tenths) ? String.valueOf((long) tenths) : String.valueOf(tenths);
    }

    /** "3–5 years", "7.5+ years", "1+ year": the stated figure in words, without the basis. */
    public String yearsText() {
        if (maxYears > minYears) {
            return years(minYears) + "–" + years(maxYears) + " years";
        }
        return years(minYears) + "+ " + (minYears == 1 ? "year" : "years");
    }

    private String years() {
        if (maxYears > minYears) {
            return years(minYears) + "–" + years(maxYears) + " yrs";
        }
        return minYears == 0 ? "Fresher" : years(minYears) + "+ yrs";
    }

    /**
     * Compact form for table cells: "Fresher", "0–2 yrs", "3+ yrs", "Pref. 5+ yrs", "Not stated" (read,
     * and it gives no years) or "Not read yet" (its description hasn't been fetched yet).
     */
    public String shortLabel() {
        if (!known()) {
            return detailsRead ? "Not stated" : "Not read yet";
        }
        return preferredOnly() ? "Pref. " + years() : years();
    }

    /** "3–5 years (stated in the posting)", "Freshers welcome (stated in the posting)". */
    public String longLabel() {
        if (!known()) {
            return detailsRead ? "Not stated in the posting" : "Posting not read yet (the next scan reads it)";
        }
        String years = minYears == 0 && maxYears < 0 ? "Freshers welcome (0 years)" : yearsText();
        return years + " (" + basis.label() + ")";
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", version);
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

    /**
     * Reads a stored result. A result from an older reader is not shown at all — it may hold a
     * guess or a misreading — only its skill list is kept; the next scan reads the posting again.
     */
    public static Requirements fromJson(Map<String, Object> m) {
        if (m.isEmpty()) {
            return UNKNOWN;
        }
        List<String> skills = new ArrayList<>();
        for (Object o : Json.arr(m, "skills")) {
            skills.add(String.valueOf(o));
        }
        int version = (int) Json.num(m, "v", 1);
        if (version < CURRENT_VERSION) {
            return new Requirements(-1, -1, Basis.UNKNOWN, "", "", "", skills, false, version);
        }
        Basis basis = switch (Json.str(m, "basis")) {
            case "STATED" -> Basis.STATED;
            case "PREFERRED" -> Basis.PREFERRED;
            default -> Basis.UNKNOWN;
        };
        return new Requirements(
                decimal(m.get("min")),
                decimal(m.get("max")),
                basis,
                Json.str(m, "evidence"),
                Json.str(m, "degree"),
                Json.str(m, "batch"),
                skills,
                m.containsKey("detailsRead") && Json.bool(m, "detailsRead"),
                version);
    }

    private static double decimal(Object v) {
        return v instanceof Number n ? n.doubleValue() : -1;
    }
}
