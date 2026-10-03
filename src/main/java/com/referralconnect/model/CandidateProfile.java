package com.referralconnect.model;

import com.referralconnect.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything a referrer needs to know about a candidate. A copy is frozen into each referral
 * request, so later profile edits do not change what a referrer has already been sent.
 *
 * @param years total years of work experience (0 for freshers), or -1 when not given; compared
 *              with each job's minimum experience
 */
public record CandidateProfile(
        String name,
        String email,
        String phone,
        String linkedin,
        String github,
        String resumeLink,
        String education,
        String experience,
        String skills,
        int years) {

    public CandidateProfile {
        name = clean(name);
        email = clean(email).toLowerCase(Locale.ROOT);
        phone = clean(phone);
        linkedin = clean(linkedin);
        github = clean(github);
        resumeLink = clean(resumeLink);
        education = clean(education);
        experience = clean(experience);
        skills = clean(skills);
        years = Math.max(-1, Math.min(years, 50));
    }

    public CandidateProfile(String name, String email, String phone, String linkedin, String github,
                            String resumeLink, String education, String experience, String skills) {
        this(name, email, phone, linkedin, github, resumeLink, education, experience, skills, -1);
    }

    public static CandidateProfile of(String name, String email) {
        return new CandidateProfile(name, email, "", "", "", "", "", "", "", -1);
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    public boolean yearsKnown() {
        return years >= 0;
    }

    /** "Fresher", "1 year", "4 years"; empty when not given. */
    public String yearsLabel() {
        if (years < 0) {
            return "";
        }
        return years == 0 ? "Fresher (0 years)" : years == 1 ? "1 year" : years + " years";
    }

    public CandidateProfile withYears(int y) {
        return new CandidateProfile(name, email, phone, linkedin, github, resumeLink, education, experience, skills, y);
    }

    /** Fields a referrer cannot work without; empty when the profile is complete enough to send. */
    public List<String> missingForReferral() {
        List<String> missing = new ArrayList<>();
        if (name.isEmpty()) {
            missing.add("name");
        }
        if (email.isEmpty()) {
            missing.add("email");
        }
        if (resumeLink.isEmpty()) {
            missing.add("resume link");
        }
        if (education.isEmpty() && experience.isEmpty()) {
            missing.add("education or experience");
        }
        if (skills.isEmpty()) {
            missing.add("skills");
        }
        return missing;
    }

    /** 0–100: how much of the profile is filled in, for the completeness meter. */
    public int completeness() {
        String[] fields = {name, email, phone, linkedin, github, resumeLink, education, experience, skills};
        int filled = 0;
        for (String f : fields) {
            if (!f.isEmpty()) {
                filled++;
            }
        }
        if (years >= 0) {
            filled++;
        }
        return Math.round(100f * filled / (fields.length + 1));
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("email", email);
        m.put("phone", phone);
        m.put("linkedin", linkedin);
        m.put("github", github);
        m.put("resumeLink", resumeLink);
        m.put("education", education);
        m.put("experience", experience);
        m.put("skills", skills);
        if (years >= 0) {
            m.put("years", years);
        }
        return m;
    }

    public static CandidateProfile fromJson(Map<String, Object> m) {
        return new CandidateProfile(
                Json.str(m, "name"), Json.str(m, "email"), Json.str(m, "phone"),
                Json.str(m, "linkedin"), Json.str(m, "github"), Json.str(m, "resumeLink"),
                Json.str(m, "education"), Json.str(m, "experience"), Json.str(m, "skills"),
                (int) Json.num(m, "years", -1));
    }
}
